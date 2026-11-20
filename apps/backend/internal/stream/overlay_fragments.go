package stream

import (
	"encoding/binary"
	"errors"
	"fmt"

	"github.com/bluenviron/mediacommon/v2/pkg/formats/fmp4"
)

// overlayFragments reads the moof+mdat fragments of an overlay stream.
// Media3's FragmentedMp4Muxer writes them without tfdt and points at their
// samples with offsets from the start of the stream, which mediacommon's
// parser doesn't take. Decode times are therefore summed up per track
// (a tfdt, where there is one, wins), and offsets are read either way.
type overlayFragments struct {
	defaults map[uint32]trackDefaults
	nextTime map[uint32]uint64
	// position is where the next fragment starts in the stream.
	position int64
}

// trackDefaults are a track's sample defaults from trex, overridden by tfhd.
type trackDefaults struct {
	duration uint32
	size     uint32
	flags    uint32
}

// trackFragment is what a traf's tfhd and tfdt say.
type trackFragment struct {
	trackID        uint32
	defaults       trackDefaults
	baseDataOffset int64
	hasBaseOffset  bool
	decodeTime     uint64
	hasDecodeTime  bool
}

const (
	tfhdBaseDataOffset      = 0x000001
	tfhdDescriptionIndex    = 0x000002
	tfhdDefaultDuration     = 0x000008
	tfhdDefaultSize         = 0x000010
	tfhdDefaultFlags        = 0x000020
	trunDataOffset          = 0x000001
	trunFirstSampleFlags    = 0x000004
	trunSampleDuration      = 0x000100
	trunSampleSize          = 0x000200
	trunSampleFlags         = 0x000400
	trunCompositionOffset   = 0x000800
	sampleIsNonSyncSample   = 0x010000
	maxSamplesPerTrackRun   = 1 << 20
	mp4BoxHeaderLength      = 8
	mp4LargeBoxHeaderLength = 16
)

var errShortBox = errors.New("MP4 box ends early")

// mp4Box is one box inside a buffer: where it starts there, and its contents after the header.
type mp4Box struct {
	kind    string
	offset  int
	payload []byte
}

// newOverlayFragments reads the track defaults from the init segment; the first fragment follows it.
func newOverlayFragments(initSegment []byte) (*overlayFragments, error) {
	reader := &overlayFragments{
		defaults: map[uint32]trackDefaults{},
		nextTime: map[uint32]uint64{},
		position: int64(len(initSegment)),
	}
	trexBoxes, err := nestedBoxes(initSegment, "moov", "mvex", "trex")
	if err != nil {
		return nil, err
	}
	for _, trex := range trexBoxes {
		if err := reader.readTrackExtends(trex.payload); err != nil {
			return nil, err
		}
	}
	return reader, nil
}

func (reader *overlayFragments) readTrackExtends(payload []byte) error {
	if len(payload) < 24 {
		return errShortBox
	}
	trackID := binary.BigEndian.Uint32(payload[4:8])
	reader.defaults[trackID] = trackDefaults{
		duration: binary.BigEndian.Uint32(payload[12:16]),
		size:     binary.BigEndian.Uint32(payload[16:20]),
		flags:    binary.BigEndian.Uint32(payload[20:24]),
	}
	return nil
}

// parts reads the samples of one fragment (boxes up to and including an mdat).
func (reader *overlayFragments) parts(fragment []byte) (fmp4.Parts, error) {
	boxes, err := childBoxes(fragment)
	if err != nil {
		return nil, err
	}
	var parts fmp4.Parts
	for _, box := range boxes {
		if box.kind != "moof" {
			continue
		}
		part, err := reader.part(fragment, box)
		if err != nil {
			return nil, err
		}
		parts = append(parts, part)
	}
	reader.position += int64(len(fragment))
	return parts, nil
}

func (reader *overlayFragments) part(fragment []byte, moof mp4Box) (*fmp4.Part, error) {
	trafs, err := childBoxes(moof.payload)
	if err != nil {
		return nil, err
	}
	part := &fmp4.Part{}
	for _, traf := range trafs {
		if traf.kind != "traf" {
			continue
		}
		track, err := reader.track(fragment, int64(moof.offset), traf.payload)
		if err != nil {
			return nil, err
		}
		part.Tracks = append(part.Tracks, track)
	}
	return part, nil
}

func (reader *overlayFragments) track(fragment []byte, moofOffset int64, trafPayload []byte) (*fmp4.PartTrack, error) {
	children, err := childBoxes(trafPayload)
	if err != nil {
		return nil, err
	}
	header, err := reader.trackFragmentOf(children)
	if err != nil {
		return nil, err
	}
	base := moofOffset
	if header.hasBaseOffset {
		base = header.baseDataOffset - reader.position
	}
	decodeTime := reader.nextTime[header.trackID]
	if header.hasDecodeTime {
		decodeTime = header.decodeTime
	}
	track := &fmp4.PartTrack{ID: int(header.trackID), BaseTime: decodeTime}
	dataPosition := base
	for _, child := range children {
		if child.kind != "trun" {
			continue
		}
		samples, end, err := readTrackRun(child.payload, fragment, base, dataPosition, header.defaults)
		if err != nil {
			return nil, err
		}
		track.Samples = append(track.Samples, samples...)
		dataPosition = end
	}
	reader.nextTime[header.trackID] = decodeTime + totalDuration(track.Samples)
	return track, nil
}

func (reader *overlayFragments) trackFragmentOf(children []mp4Box) (trackFragment, error) {
	var header trackFragment
	foundHeader := false
	for _, child := range children {
		switch child.kind {
		case "tfhd":
			parsed, err := reader.readTrackFragmentHeader(child.payload)
			if err != nil {
				return header, err
			}
			parsed.decodeTime, parsed.hasDecodeTime = header.decodeTime, header.hasDecodeTime
			header = parsed
			foundHeader = true
		case "tfdt":
			decodeTime, err := readDecodeTime(child.payload)
			if err != nil {
				return header, err
			}
			header.decodeTime, header.hasDecodeTime = decodeTime, true
		}
	}
	if !foundHeader {
		return header, errors.New("traf without tfhd")
	}
	return header, nil
}

func (reader *overlayFragments) readTrackFragmentHeader(payload []byte) (trackFragment, error) {
	cursor := boxCursor{data: payload}
	flags := cursor.uint32() & 0xffffff
	header := trackFragment{trackID: cursor.uint32()}
	header.defaults = reader.defaults[header.trackID]
	if flags&tfhdBaseDataOffset != 0 {
		header.baseDataOffset = int64(cursor.uint64())
		header.hasBaseOffset = true
	}
	if flags&tfhdDescriptionIndex != 0 {
		cursor.uint32()
	}
	if flags&tfhdDefaultDuration != 0 {
		header.defaults.duration = cursor.uint32()
	}
	if flags&tfhdDefaultSize != 0 {
		header.defaults.size = cursor.uint32()
	}
	if flags&tfhdDefaultFlags != 0 {
		header.defaults.flags = cursor.uint32()
	}
	return header, cursor.err
}

func readDecodeTime(payload []byte) (uint64, error) {
	cursor := boxCursor{data: payload}
	version := cursor.uint32() >> 24
	if version == 1 {
		return cursor.uint64(), cursor.err
	}
	return uint64(cursor.uint32()), cursor.err
}

// readTrackRun reads one trun's samples; their data starts at base+data_offset, or at
// continueAt when the run has no offset. It returns where the run's data ends.
func readTrackRun(payload []byte, fragment []byte, base int64, continueAt int64, defaults trackDefaults) ([]*fmp4.Sample, int64, error) {
	cursor := boxCursor{data: payload}
	flags := cursor.uint32() & 0xffffff
	count := cursor.uint32()
	if count > maxSamplesPerTrackRun {
		return nil, 0, fmt.Errorf("trun with %d samples", count)
	}
	position := continueAt
	if flags&trunDataOffset != 0 {
		position = base + int64(int32(cursor.uint32()))
	}
	firstFlags, hasFirstFlags := uint32(0), flags&trunFirstSampleFlags != 0
	if hasFirstFlags {
		firstFlags = cursor.uint32()
	}
	samples := make([]*fmp4.Sample, 0, count)
	for index := uint32(0); index < count; index++ {
		sample, size := readRunSample(&cursor, flags, defaults)
		if index == 0 && hasFirstFlags && flags&trunSampleFlags == 0 {
			sample.IsNonSyncSample = firstFlags&sampleIsNonSyncSample != 0
		}
		if cursor.err != nil {
			return nil, 0, cursor.err
		}
		if position < 0 || position+int64(size) > int64(len(fragment)) {
			return nil, 0, fmt.Errorf("sample data at %d+%d is outside the fragment (%d bytes)", position, size, len(fragment))
		}
		sample.Payload = fragment[position : position+int64(size)]
		position += int64(size)
		samples = append(samples, sample)
	}
	return samples, position, nil
}

// readRunSample reads one sample entry of a trun; the payload is filled in by the caller.
func readRunSample(cursor *boxCursor, flags uint32, defaults trackDefaults) (*fmp4.Sample, uint32) {
	duration, size, sampleFlags := defaults.duration, defaults.size, defaults.flags
	compositionOffset := int32(0)
	if flags&trunSampleDuration != 0 {
		duration = cursor.uint32()
	}
	if flags&trunSampleSize != 0 {
		size = cursor.uint32()
	}
	if flags&trunSampleFlags != 0 {
		sampleFlags = cursor.uint32()
	}
	if flags&trunCompositionOffset != 0 {
		// Signed in version 1, unsigned in version 0; offsets fit in 31 bits either way.
		compositionOffset = int32(cursor.uint32())
	}
	sample := &fmp4.Sample{
		Duration:        duration,
		PTSOffset:       compositionOffset,
		IsNonSyncSample: sampleFlags&sampleIsNonSyncSample != 0,
	}
	return sample, size
}

func totalDuration(samples []*fmp4.Sample) uint64 {
	total := uint64(0)
	for _, sample := range samples {
		total += uint64(sample.Duration)
	}
	return total
}

// nestedBoxes finds the boxes at a path such as moov/mvex/trex.
func nestedBoxes(data []byte, path ...string) ([]mp4Box, error) {
	boxes, err := childBoxes(data)
	if err != nil {
		return nil, err
	}
	var found []mp4Box
	for _, box := range boxes {
		if box.kind != path[0] {
			continue
		}
		if len(path) == 1 {
			found = append(found, box)
			continue
		}
		nested, err := nestedBoxes(box.payload, path[1:]...)
		if err != nil {
			return nil, err
		}
		found = append(found, nested...)
	}
	return found, nil
}

// childBoxes splits a buffer into the boxes it holds.
func childBoxes(data []byte) ([]mp4Box, error) {
	var boxes []mp4Box
	offset := 0
	for offset < len(data) {
		box, size, err := boxAt(data, offset)
		if err != nil {
			return nil, err
		}
		boxes = append(boxes, box)
		offset += size
	}
	return boxes, nil
}

func boxAt(data []byte, offset int) (mp4Box, int, error) {
	if len(data)-offset < mp4BoxHeaderLength {
		return mp4Box{}, 0, errShortBox
	}
	size := int64(binary.BigEndian.Uint32(data[offset:]))
	kind := string(data[offset+4 : offset+8])
	headerLength := mp4BoxHeaderLength
	switch size {
	case 0:
		size = int64(len(data) - offset)
	case 1:
		if len(data)-offset < mp4LargeBoxHeaderLength {
			return mp4Box{}, 0, errShortBox
		}
		size = int64(binary.BigEndian.Uint64(data[offset+8:]))
		headerLength = mp4LargeBoxHeaderLength
	}
	if size < int64(headerLength) || size > int64(len(data)-offset) {
		return mp4Box{}, 0, fmt.Errorf("MP4 box %q has a bad size %d", kind, size)
	}
	end := offset + int(size)
	return mp4Box{kind: kind, offset: offset, payload: data[offset+headerLength : end]}, int(size), nil
}

// boxCursor reads big-endian fields; the first read past the end sets err and later reads return 0.
type boxCursor struct {
	data     []byte
	position int
	err      error
}

func (cursor *boxCursor) uint32() uint32 {
	if cursor.err != nil || len(cursor.data)-cursor.position < 4 {
		cursor.err = errShortBox
		return 0
	}
	value := binary.BigEndian.Uint32(cursor.data[cursor.position:])
	cursor.position += 4
	return value
}

func (cursor *boxCursor) uint64() uint64 {
	if cursor.err != nil || len(cursor.data)-cursor.position < 8 {
		cursor.err = errShortBox
		return 0
	}
	value := binary.BigEndian.Uint64(cursor.data[cursor.position:])
	cursor.position += 8
	return value
}
