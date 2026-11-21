package stream

import (
	"bufio"
	"bytes"
	"context"
	"errors"
	"fmt"
	"io"
	"log"
	"net/http"
	"net/url"
	"strconv"

	"github.com/Letsmoe/otter/internal/errorreport"
	"github.com/bluenviron/mediacommon/v2/pkg/codecs/mpeg4audio"
	"github.com/bluenviron/mediacommon/v2/pkg/formats/fmp4"
	mp4codecs "github.com/bluenviron/mediacommon/v2/pkg/formats/mp4/codecs"
	"github.com/bluenviron/mediacommon/v2/pkg/formats/mpegts"
	tscodecs "github.com/bluenviron/mediacommon/v2/pkg/formats/mpegts/codecs"
)

// A TV session is one stream a DLNA TV gets for as long as it is connected
// to the phone: the idle screen, loading screens and every title played
// follow each other in it, so the TV is handed a URL only once. The phone
// encodes each of them as a job of the session (all in the same format) and
// hands them out one after the other on loopback; they reach the TV as one
// MPEG-TS stream whose timestamps run on across jobs.
//
//	GET|HEAD /api/stream/tv?session=<id>
//
// The phone's side, asked again whenever a job ends:
//
//	GET <OVERLAY_STREAM_URL>/session/<id>/next?at=<microseconds>
//
// It answers once it has a job: 200 with the job's fragmented MP4 until the
// job ends (a cut job ends mid-fragment), or 404 when the session is over.
// at is where the job starts on the session's timeline, so the phone can tell
// which moment the TV shows.

// A run of jobs this long that failed before writing anything means the phone can't make any.
const maxFailedTelevisionJobs = 3

var (
	errTelevisionSessionGone = errors.New("the TV session ended")
	errNoSessionVideo        = errors.New("TV session job has no H.264 video")
)

// The audio track's format until a job says otherwise: what the phone encodes, 48 kHz stereo AAC.
var defaultSessionAudio = mpeg4audio.AudioSpecificConfig{
	Type:          mpeg4audio.ObjectTypeAACLC,
	SampleRate:    48000,
	ChannelConfig: 2,
	ChannelCount:  2,
}

func (p *Proxy) serveTelevisionSession(w http.ResponseWriter, r *http.Request) {
	if p.overlaySource == "" {
		http.Error(w, errNoOverlaySource.Error(), http.StatusNotFound)
		return
	}
	session := r.URL.Query().Get("session")
	if !overlayJobPattern.MatchString(session) {
		http.Error(w, "bad session", http.StatusBadRequest)
		return
	}
	w.Header().Set("Content-Type", overlayContentType)
	// Some TVs give up on a HEAD that takes longer than a second: it is answered without the phone.
	if r.Method == http.MethodHead {
		w.WriteHeader(http.StatusOK)
		return
	}
	err := p.relayTelevisionSession(r.Context(), w, session)
	if err == nil || r.Context().Err() != nil {
		return
	}
	log.Printf("TV session stream: %v", err)
	errorreport.Capture(r.Context(), err, "stream.tv_session", map[string]any{"session": session, "user_agent": r.UserAgent()})
}

// relayTelevisionSession writes the session's jobs to the TV until the TV hangs up or the session ends.
func (p *Proxy) relayTelevisionSession(ctx context.Context, w http.ResponseWriter, session string) error {
	output := &televisionWriter{response: w}
	relay, err := newTelevisionRelay(output)
	if err != nil {
		return err
	}
	failedJobs := 0
	for {
		body, err := p.openSessionJob(ctx, session, relay.nextJobMicroseconds())
		if errors.Is(err, errTelevisionSessionGone) {
			relay.answerUnstarted(http.StatusNotFound, err)
			return nil
		}
		if err != nil {
			relay.answerUnstarted(http.StatusBadGateway, err)
			return err
		}
		written, jobErr := relay.relayJob(body)
		body.Close()
		if output.err != nil || ctx.Err() != nil {
			return output.err
		}
		failedJobs = countFailedJob(failedJobs, written, jobErr)
		if failedJobs >= maxFailedTelevisionJobs {
			relay.answerUnstarted(http.StatusBadGateway, jobErr)
			return fmt.Errorf("%d TV session jobs in a row failed: %w", failedJobs, jobErr)
		}
	}
}

// countFailedJob counts jobs in a row that broke before writing anything; any written job resets it.
func countFailedJob(failedJobs int, written bool, jobErr error) int {
	if written || jobErr == nil {
		return 0
	}
	log.Printf("TV session job: %v", jobErr)
	return failedJobs + 1
}

// openSessionJob waits for the phone's next job; errTelevisionSessionGone when there is none anymore.
func (p *Proxy) openSessionJob(ctx context.Context, session string, atMicroseconds int64) (io.ReadCloser, error) {
	jobURL := p.overlaySource + "/session/" + url.PathEscape(session) + "/next?at=" + strconv.FormatInt(atMicroseconds, 10)
	request, err := http.NewRequestWithContext(ctx, http.MethodGet, jobURL, nil)
	if err != nil {
		return nil, err
	}
	response, err := overlayClient.Do(request)
	if err != nil {
		return nil, err
	}
	if response.StatusCode == http.StatusNotFound {
		response.Body.Close()
		return nil, errTelevisionSessionGone
	}
	if response.StatusCode != http.StatusOK {
		response.Body.Close()
		return nil, fmt.Errorf("encoder answered HTTP %d", response.StatusCode)
	}
	return response.Body, nil
}

// televisionWriter remembers the first failed write: the TV went away, not the phone.
type televisionWriter struct {
	response http.ResponseWriter
	err      error
	written  bool
}

func (writer *televisionWriter) Write(data []byte) (int, error) {
	if writer.err != nil {
		return 0, writer.err
	}
	writer.written = true
	count, err := writer.response.Write(data)
	writer.err = err
	return count, err
}

func (writer *televisionWriter) flush() {
	if flusher, ok := writer.response.(http.Flusher); ok {
		flusher.Flush()
	}
}

// televisionRelay is the session's MPEG-TS stream: fixed H.264 and AAC tracks that every job's samples go into.
type televisionRelay struct {
	output   *televisionWriter
	remuxer  *tsRemuxer
	video    *mpegts.Track
	audio    *mpegts.Track
	aligning bool
}

func newTelevisionRelay(output *televisionWriter) (*televisionRelay, error) {
	relay := &televisionRelay{
		output: output,
		video:  &mpegts.Track{Codec: &tscodecs.H264{}},
		audio:  &mpegts.Track{Codec: &tscodecs.MPEG4Audio{Config: defaultSessionAudio}},
	}
	writer := &mpegts.Writer{W: output, Tracks: []*mpegts.Track{relay.video, relay.audio}}
	if err := writer.Initialize(); err != nil {
		return nil, err
	}
	relay.remuxer = &tsRemuxer{writer: writer, tracks: map[int]*remuxTrack{}, shiftTicks: timestampOffset, endTicks: timestampOffset}
	return relay, nil
}

// nextJobMicroseconds is where the next job starts on the session's timeline: where the last one ended.
func (relay *televisionRelay) nextJobMicroseconds() int64 {
	return (relay.remuxer.endTicks - timestampOffset) * 1_000_000 / mpegtsClockRate
}

// answerUnstarted reports an error to a TV that got no video yet; later, the stream just ends.
func (relay *televisionRelay) answerUnstarted(status int, err error) {
	if relay.output.written {
		return
	}
	message := "TV session"
	if err != nil {
		message += ": " + err.Error()
	}
	http.Error(relay.output.response, message, status)
}

// relayJob writes one job's fragments; a job that ends, even inside a fragment, is simply over.
// It tells whether any of the job's samples reached the TV.
func (relay *televisionRelay) relayJob(body io.Reader) (bool, error) {
	boxes := bufio.NewReaderSize(body, 1<<20)
	initSegment, err := readOverlayInit(boxes)
	if err != nil {
		return false, err
	}
	fragments, err := newOverlayFragments(initSegment)
	if err != nil {
		return false, err
	}
	if err := relay.startJob(initSegment); err != nil {
		return false, err
	}
	written := false
	for {
		fragment, err := readOverlayFragment(boxes)
		if errors.Is(err, io.EOF) || errors.Is(err, io.ErrUnexpectedEOF) {
			return written, nil
		}
		if err != nil {
			return written, err
		}
		parts, err := fragments.parts(fragment)
		if err != nil {
			return written, fmt.Errorf("fMP4 fragment: %w", err)
		}
		if err := relay.writeParts(parts); err != nil {
			return written, err
		}
		written = true
		relay.output.flush()
	}
}

// startJob points the job's fMP4 tracks at the session's tracks; its first samples continue where the last job ended.
func (relay *televisionRelay) startJob(initSegment []byte) error {
	var init fmp4.Init
	if err := init.Unmarshal(bytes.NewReader(initSegment)); err != nil {
		return fmt.Errorf("fMP4 init: %w", err)
	}
	tracks := map[int]*remuxTrack{}
	hasVideo := false
	for _, initTrack := range init.Tracks {
		track := relay.sessionTrackFor(initTrack)
		if track == nil {
			continue
		}
		tracks[initTrack.ID] = track
		if track.output == relay.video {
			hasVideo = true
		}
	}
	if !hasVideo {
		return errNoSessionVideo
	}
	relay.remuxer.tracks = tracks
	relay.aligning = true
	return nil
}

// sessionTrackFor maps a job's track onto the session's track of its kind, with the job's own parameter sets.
func (relay *televisionRelay) sessionTrackFor(initTrack *fmp4.InitTrack) *remuxTrack {
	switch codec := initTrack.Codec.(type) {
	case *mp4codecs.H264:
		return &remuxTrack{timeScale: initTrack.TimeScale, output: relay.video, parameterSets: [][]byte{codec.SPS, codec.PPS}, write: writeH264Sample}
	case *mp4codecs.MPEG4Audio:
		// ADTS headers are written from this config, so a job in another format still plays.
		relay.audio.Codec.(*tscodecs.MPEG4Audio).Config = codec.Config
		return &remuxTrack{timeScale: initTrack.TimeScale, output: relay.audio, write: writeAACSample}
	}
	return nil
}

// writeParts writes a fragment; a job's first fragment decides how far its timestamps are moved.
func (relay *televisionRelay) writeParts(parts fmp4.Parts) error {
	if !relay.aligning {
		return relay.remuxer.writeParts(parts)
	}
	relay.remuxer.shiftTicks = 0
	samples := relay.remuxer.samplesInDecodeOrder(parts)
	if len(samples) == 0 {
		return nil
	}
	shift := relay.remuxer.endTicks - samples[0].dts
	for index := range samples {
		samples[index].dts += shift
		samples[index].pts += shift
	}
	relay.remuxer.shiftTicks = shift
	relay.aligning = false
	return relay.remuxer.writeSamples(samples)
}
