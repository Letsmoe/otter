package stream

import (
	"bytes"
	"errors"
	"fmt"
	"io"
	"sort"

	"github.com/bluenviron/mediacommon/v2/pkg/codecs/h264"
	"github.com/bluenviron/mediacommon/v2/pkg/codecs/h265"
	"github.com/bluenviron/mediacommon/v2/pkg/formats/fmp4"
	mp4codecs "github.com/bluenviron/mediacommon/v2/pkg/formats/mp4/codecs"
	"github.com/bluenviron/mediacommon/v2/pkg/formats/mpegts"
	tscodecs "github.com/bluenviron/mediacommon/v2/pkg/formats/mpegts/codecs"
)

// TVs play fragmented MP4 sent as one endless stream badly (a Hisense showed
// 3 fps), while every DLNA renderer handles MPEG-TS. HLS streams with fMP4
// segments are therefore repackaged into MPEG-TS on the way to the TV; the
// video and audio are copied, not re-encoded.

const mpegtsClockRate = 90000

// MPEG-TS clock references sit 0.1 s before the first decode time, so
// timestamps start one second in to stay positive.
const timestampOffset = mpegtsClockRate

var errNoRemuxableVideo = errors.New("fMP4 has no H.264 or H.265 video")

// tsRemuxer writes the fragments of one fMP4 stream as MPEG-TS.
type tsRemuxer struct {
	writer *mpegts.Writer
	tracks map[int]*remuxTrack
}

// remuxTrack is one fMP4 track and how its samples go into MPEG-TS.
type remuxTrack struct {
	timeScale uint32
	output    *mpegts.Track
	write     func(writer *mpegts.Writer, track *remuxTrack, sample timedSample) error
	// Codec parameters (SPS/PPS, VPS/SPS/PPS) that fMP4 keeps in the init segment and MPEG-TS needs before keyframes.
	parameterSets [][]byte
}

// timedSample is a sample with its timestamps on the 90 kHz MPEG-TS clock.
type timedSample struct {
	track *remuxTrack
	dts   int64
	pts   int64
	data  []byte
}

// newTSRemuxer reads the init segment; tracks whose codec MPEG-TS can't carry are left out.
func newTSRemuxer(output io.Writer, initSegment []byte) (*tsRemuxer, error) {
	var init fmp4.Init
	if err := init.Unmarshal(bytes.NewReader(initSegment)); err != nil {
		return nil, fmt.Errorf("fMP4 init: %w", err)
	}
	remuxer := &tsRemuxer{tracks: map[int]*remuxTrack{}}
	outputTracks := []*mpegts.Track{}
	hasVideo := false
	for _, initTrack := range init.Tracks {
		track := remuxTrackFor(initTrack)
		if track == nil {
			continue
		}
		remuxer.tracks[initTrack.ID] = track
		outputTracks = append(outputTracks, track.output)
		if track.output.Codec.IsVideo() {
			hasVideo = true
		}
	}
	if !hasVideo {
		return nil, errNoRemuxableVideo
	}
	remuxer.writer = &mpegts.Writer{W: output, Tracks: outputTracks}
	if err := remuxer.writer.Initialize(); err != nil {
		return nil, err
	}
	return remuxer, nil
}

// remuxTrackFor maps an fMP4 codec to its MPEG-TS counterpart; nil when there is none.
func remuxTrackFor(initTrack *fmp4.InitTrack) *remuxTrack {
	track := &remuxTrack{timeScale: initTrack.TimeScale}
	switch codec := initTrack.Codec.(type) {
	case *mp4codecs.H264:
		track.output = &mpegts.Track{Codec: &tscodecs.H264{}}
		track.parameterSets = [][]byte{codec.SPS, codec.PPS}
		track.write = writeH264Sample
	case *mp4codecs.H265:
		track.output = &mpegts.Track{Codec: &tscodecs.H265{}}
		track.parameterSets = [][]byte{codec.VPS, codec.SPS, codec.PPS}
		track.write = writeH265Sample
	case *mp4codecs.MPEG4Audio:
		track.output = &mpegts.Track{Codec: &tscodecs.MPEG4Audio{Config: codec.Config}}
		track.write = writeAACSample
	case *mp4codecs.AC3:
		track.output = &mpegts.Track{Codec: &tscodecs.AC3{SampleRate: codec.SampleRate, ChannelCount: codec.ChannelCount}}
		track.write = writeAC3Sample
	default:
		return nil
	}
	return track
}

// writeFragment repackages one fMP4 segment (one or more moof+mdat pairs).
func (remuxer *tsRemuxer) writeFragment(fragment []byte) error {
	var parts fmp4.Parts
	if err := parts.Unmarshal(fragment); err != nil {
		return fmt.Errorf("fMP4 fragment: %w", err)
	}
	for _, sample := range remuxer.samplesInDecodeOrder(parts) {
		if err := sample.track.write(remuxer.writer, sample.track, sample); err != nil {
			return err
		}
	}
	return nil
}

// samplesInDecodeOrder interleaves the tracks of a fragment, so audio and video arrive together.
func (remuxer *tsRemuxer) samplesInDecodeOrder(parts fmp4.Parts) []timedSample {
	samples := []timedSample{}
	for _, part := range parts {
		for _, partTrack := range part.Tracks {
			samples = append(samples, remuxer.timedSamples(partTrack)...)
		}
	}
	sort.SliceStable(samples, func(left, right int) bool {
		return samples[left].dts < samples[right].dts
	})
	return samples
}

func (remuxer *tsRemuxer) timedSamples(partTrack *fmp4.PartTrack) []timedSample {
	track, found := remuxer.tracks[partTrack.ID]
	if !found {
		return nil
	}
	samples := make([]timedSample, 0, len(partTrack.Samples))
	decodeTime := int64(partTrack.BaseTime)
	for _, sample := range partTrack.Samples {
		samples = append(samples, timedSample{
			track: track,
			dts:   track.clockTicks(decodeTime),
			pts:   track.clockTicks(decodeTime + int64(sample.PTSOffset)),
			data:  sample.Payload,
		})
		decodeTime += int64(sample.Duration)
	}
	return samples
}

// clockTicks converts a time in the track's time scale to the MPEG-TS clock.
func (track *remuxTrack) clockTicks(value int64) int64 {
	return value*mpegtsClockRate/int64(track.timeScale) + timestampOffset
}

func writeH264Sample(writer *mpegts.Writer, track *remuxTrack, sample timedSample) error {
	var accessUnit h264.AVCC
	if err := accessUnit.Unmarshal(sample.data); err != nil {
		return fmt.Errorf("H.264 sample: %w", err)
	}
	if h264.IsRandomAccess(accessUnit) && !hasH264ParameterSets(accessUnit) {
		accessUnit = append(append([][]byte{}, track.parameterSets...), accessUnit...)
	}
	return writer.WriteH264(track.output, sample.pts, sample.dts, accessUnit)
}

func hasH264ParameterSets(accessUnit [][]byte) bool {
	for _, unit := range accessUnit {
		if len(unit) > 0 && h264.NALUType(unit[0]&0x1F) == h264.NALUTypeSPS {
			return true
		}
	}
	return false
}

func writeH265Sample(writer *mpegts.Writer, track *remuxTrack, sample timedSample) error {
	var accessUnit h264.AVCC
	if err := accessUnit.Unmarshal(sample.data); err != nil {
		return fmt.Errorf("H.265 sample: %w", err)
	}
	if h265.IsRandomAccess(accessUnit) && !hasH265ParameterSets(accessUnit) {
		accessUnit = append(append([][]byte{}, track.parameterSets...), accessUnit...)
	}
	return writer.WriteH265(track.output, sample.pts, sample.dts, accessUnit)
}

func hasH265ParameterSets(accessUnit [][]byte) bool {
	for _, unit := range accessUnit {
		if len(unit) > 0 && h265.NALUType((unit[0]>>1)&0x3F) == h265.NALUType_SPS_NUT {
			return true
		}
	}
	return false
}

func writeAACSample(writer *mpegts.Writer, track *remuxTrack, sample timedSample) error {
	return writer.WriteMPEG4Audio(track.output, sample.pts, [][]byte{sample.data})
}

func writeAC3Sample(writer *mpegts.Writer, track *remuxTrack, sample timedSample) error {
	return writer.WriteAC3(track.output, sample.pts, sample.data)
}
