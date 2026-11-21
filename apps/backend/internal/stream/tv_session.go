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
	"time"

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

// Waiting for a job this long without writing anything, the TV gets the bridge clip: the phone
// keeps about 2.5 s in the TV's buffer, so this leaves a second before the TV would run dry,
// and switches that are quick enough get no bridge (it would show for its whole second).
var bridgeAfter = 1500 * time.Millisecond

// The most a bridge clip from the phone may weigh.
const maxBridgeClipBytes = 16 << 20

// The header naming the job a /next answer streams.
const sessionJobHeader = "Otter-Job"

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
	relay.onJobStarted = func(jobID string, atMicroseconds int64) {
		go p.notifyJobStarted(ctx, session, jobID, atMicroseconds)
	}
	bridges := make(chan *bridgeClip, 1)
	go func() { bridges <- p.fetchBridgeClip(ctx, session) }()
	failedJobs := 0
	for {
		jobs := make(chan preparedJob, 1)
		go p.prepareJob(ctx, session, relay.nextJobMicroseconds(), jobs)
		prepared, err := relay.awaitJob(ctx, jobs, bridges)
		if err != nil {
			return relay.output.err
		}
		if errors.Is(prepared.err, errTelevisionSessionGone) {
			relay.answerUnstarted(http.StatusNotFound, prepared.err)
			return nil
		}
		if prepared.body == nil {
			relay.answerUnstarted(http.StatusBadGateway, prepared.err)
			return prepared.err
		}
		written, jobErr := relay.relayJob(prepared)
		prepared.body.Close()
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

// preparedJob is the phone's next job, read up to its first fragment: until then the TV may need bridging.
// firstFragment is nil when the job ended before it sent one. body is set whenever the phone answered.
type preparedJob struct {
	body          io.ReadCloser
	jobID         string
	boxes         *bufio.Reader
	initSegment   []byte
	firstFragment []byte
	err           error
}

// prepareJob asks the phone for its next job and reads it up to its first fragment, then hands it to jobs.
func (p *Proxy) prepareJob(ctx context.Context, session string, atMicroseconds int64, jobs chan<- preparedJob) {
	prepared := preparedJob{}
	response, err := p.openSessionJob(ctx, session, atMicroseconds)
	if err != nil {
		prepared.err = err
		jobs <- prepared
		return
	}
	prepared.body = response.Body
	prepared.jobID = response.Header.Get(sessionJobHeader)
	prepared.boxes = bufio.NewReaderSize(response.Body, 1<<20)
	prepared.initSegment, prepared.err = readOverlayInit(prepared.boxes)
	if prepared.err == nil {
		prepared.firstFragment, prepared.err = readFirstFragment(prepared.boxes)
	}
	jobs <- prepared
}

// readFirstFragment reads a job's first fragment; nil without an error when the job ended before it.
func readFirstFragment(boxes io.Reader) ([]byte, error) {
	fragment, err := readOverlayFragment(boxes)
	if errors.Is(err, io.EOF) || errors.Is(err, io.ErrUnexpectedEOF) {
		return nil, nil
	}
	return fragment, err
}

// openSessionJob waits for the phone's next job; errTelevisionSessionGone when there is none anymore.
func (p *Proxy) openSessionJob(ctx context.Context, session string, atMicroseconds int64) (*http.Response, error) {
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
	return response, nil
}

// notifyJobStarted tells the phone where a job's first sample went, so its pacing and positions follow.
func (p *Proxy) notifyJobStarted(ctx context.Context, session string, jobID string, atMicroseconds int64) {
	if jobID == "" {
		return
	}
	target := p.overlaySource + "/session/" + url.PathEscape(session) + "/started?job=" + url.QueryEscape(jobID) + "&at=" + strconv.FormatInt(atMicroseconds, 10)
	request, err := http.NewRequestWithContext(ctx, http.MethodGet, target, nil)
	if err != nil {
		return
	}
	response, err := overlayClient.Do(request)
	if err != nil {
		log.Printf("TV session: telling the phone where job %s started: %v", jobID, err)
		return
	}
	response.Body.Close()
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
	// bridge fills the stream while a job is slow to start; nil until the phone sent it.
	bridge *bridgeClip
	// nextBridgeAt is when, still waiting for a job, the bridge is written again.
	nextBridgeAt time.Time
	// bridgesInGap counts the bridges written while waiting for the current job.
	bridgesInGap int
	// onJobStarted hears where a job's first sample went on the session's timeline.
	onJobStarted func(jobID string, atMicroseconds int64)
}

func newTelevisionRelay(output *televisionWriter) (*televisionRelay, error) {
	relay := &televisionRelay{
		output: output,
		video:  &mpegts.Track{Codec: &tscodecs.H264{}},
		audio:  &mpegts.Track{Codec: &tscodecs.MPEG4Audio{Config: defaultSessionAudio}},
		// The first job gets as long as any other before it is bridged.
		nextBridgeAt: time.Now().Add(bridgeAfter),
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

// awaitJob waits for the next job's first fragment. Meanwhile, once nothing reached the TV for
// bridgeAfter, it writes the bridge clip, again each time one has played out, so the TV never
// runs out of picture (it would stop and buffer for seconds). An error means the TV hung up.
func (relay *televisionRelay) awaitJob(ctx context.Context, jobs <-chan preparedJob, bridges <-chan *bridgeClip) (preparedJob, error) {
	timer := time.NewTimer(relay.untilBridge())
	defer timer.Stop()
	for {
		select {
		case prepared := <-jobs:
			return prepared, nil
		case clip := <-bridges:
			relay.bridge = clip
			timer.Reset(relay.untilBridge())
		case <-timer.C:
			if err := relay.writeBridge(); err != nil {
				return preparedJob{}, err
			}
			timer.Reset(relay.untilBridge())
		case <-ctx.Done():
			go closeWhenPrepared(jobs)
			return preparedJob{}, ctx.Err()
		}
	}
}

// closeWhenPrepared closes the body of a job nobody waits for anymore.
func closeWhenPrepared(jobs <-chan preparedJob) {
	prepared := <-jobs
	if prepared.body != nil {
		prepared.body.Close()
	}
}

func (relay *televisionRelay) untilBridge() time.Duration {
	if relay.bridge == nil {
		return time.Hour
	}
	return time.Until(relay.nextBridgeAt)
}

// writeBridge appends one bridge clip; the next follows once this one has played out.
func (relay *televisionRelay) writeBridge() error {
	if err := relay.startJob(relay.bridge.initSegment); err != nil {
		return err
	}
	for _, parts := range relay.bridge.fragments {
		if err := relay.writeParts(parts); err != nil {
			return err
		}
	}
	relay.output.flush()
	relay.bridgesInGap++
	relay.nextBridgeAt = time.Now().Add(relay.bridge.duration)
	return relay.output.err
}

// relayJob writes one job's fragments; a job that ends, even inside a fragment, is simply over.
// It tells whether any of the job's samples reached the TV.
func (relay *televisionRelay) relayJob(prepared preparedJob) (bool, error) {
	if prepared.err != nil {
		return false, prepared.err
	}
	fragments, err := newOverlayFragments(prepared.initSegment)
	if err != nil {
		return false, err
	}
	if err := relay.startJob(prepared.initSegment); err != nil {
		return false, err
	}
	if prepared.firstFragment == nil {
		return false, nil
	}
	relay.announceJobStart(prepared.jobID)
	fragment := prepared.firstFragment
	written := false
	for {
		if err := relay.writeFragment(fragments, fragment); err != nil {
			return written, err
		}
		written = true
		fragment, err = readOverlayFragment(prepared.boxes)
		if errors.Is(err, io.EOF) || errors.Is(err, io.ErrUnexpectedEOF) {
			log.Printf("TV session: job %s ended at %.1f s", prepared.jobID, microsecondsToSeconds(relay.nextJobMicroseconds()))
			return written, nil
		}
		if err != nil {
			return written, err
		}
	}
}

// announceJobStart logs and reports where the job's first sample goes: after any bridges written while it was slow.
func (relay *televisionRelay) announceJobStart(jobID string) {
	startMicroseconds := relay.nextJobMicroseconds()
	log.Printf("TV session: job %s starts at %.1f s after %d bridge clips", jobID, microsecondsToSeconds(startMicroseconds), relay.bridgesInGap)
	relay.bridgesInGap = 0
	if relay.onJobStarted != nil {
		relay.onJobStarted(jobID, startMicroseconds)
	}
}

// writeFragment writes one of the job's fragments; while the job runs, the bridge waits a full bridgeAfter.
func (relay *televisionRelay) writeFragment(fragments *overlayFragments, fragment []byte) error {
	parts, err := fragments.parts(fragment)
	if err != nil {
		return fmt.Errorf("fMP4 fragment: %w", err)
	}
	if err := relay.writeParts(parts); err != nil {
		return err
	}
	relay.output.flush()
	relay.nextBridgeAt = time.Now().Add(bridgeAfter)
	return nil
}

func microsecondsToSeconds(microseconds int64) float64 {
	return float64(microseconds) / 1_000_000
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

// bridgeClip is the phone's second of spinner, parsed once per session and written whole whenever needed.
type bridgeClip struct {
	initSegment []byte
	fragments   []fmp4.Parts
	duration    time.Duration
}

// fetchBridgeClip gets the session's bridge from the phone; nil when it has none, then switches aren't bridged.
func (p *Proxy) fetchBridgeClip(ctx context.Context, session string) *bridgeClip {
	data, err := p.readBridgeClip(ctx, session)
	if err != nil {
		log.Printf("TV session: no bridge clip: %v", err)
		return nil
	}
	clip, err := parseBridgeClip(data)
	if err != nil {
		log.Printf("TV session: bad bridge clip: %v", err)
		return nil
	}
	log.Printf("TV session: bridge clip of %s, %d KiB", clip.duration, len(data)/1024)
	return clip
}

func (p *Proxy) readBridgeClip(ctx context.Context, session string) ([]byte, error) {
	target := p.overlaySource + "/session/" + url.PathEscape(session) + "/bridge"
	request, err := http.NewRequestWithContext(ctx, http.MethodGet, target, nil)
	if err != nil {
		return nil, err
	}
	response, err := overlayClient.Do(request)
	if err != nil {
		return nil, err
	}
	defer response.Body.Close()
	if response.StatusCode != http.StatusOK {
		return nil, fmt.Errorf("phone answered HTTP %d", response.StatusCode)
	}
	return io.ReadAll(io.LimitReader(response.Body, maxBridgeClipBytes))
}

// parseBridgeClip splits a whole fragmented MP4 into its init segment and parsed fragments.
func parseBridgeClip(data []byte) (*bridgeClip, error) {
	reader := bytes.NewReader(data)
	initSegment, err := readOverlayInit(reader)
	if err != nil {
		return nil, err
	}
	fragments, err := newOverlayFragments(initSegment)
	if err != nil {
		return nil, err
	}
	clip := &bridgeClip{initSegment: initSegment}
	for {
		fragment, err := readOverlayFragment(reader)
		if errors.Is(err, io.EOF) {
			break
		}
		if err != nil {
			return nil, err
		}
		parts, err := fragments.parts(fragment)
		if err != nil {
			return nil, fmt.Errorf("fMP4 fragment: %w", err)
		}
		clip.fragments = append(clip.fragments, parts)
	}
	if len(clip.fragments) == 0 {
		return nil, errors.New("the bridge clip has no fragments")
	}
	clip.duration, err = videoDuration(initSegment, clip.fragments)
	return clip, err
}

// videoDuration adds up the video samples of the fragments.
func videoDuration(initSegment []byte, fragments []fmp4.Parts) (time.Duration, error) {
	var init fmp4.Init
	if err := init.Unmarshal(bytes.NewReader(initSegment)); err != nil {
		return 0, fmt.Errorf("fMP4 init: %w", err)
	}
	videoTrack := videoTrackOf(init)
	if videoTrack == nil {
		return 0, errNoSessionVideo
	}
	var ticks uint64
	for _, parts := range fragments {
		ticks += trackTicks(parts, videoTrack.ID)
	}
	return time.Duration(ticks * uint64(time.Second) / uint64(videoTrack.TimeScale)), nil
}

func videoTrackOf(init fmp4.Init) *fmp4.InitTrack {
	for _, track := range init.Tracks {
		if _, isVideo := track.Codec.(*mp4codecs.H264); isVideo {
			return track
		}
	}
	return nil
}

func trackTicks(parts fmp4.Parts, trackID int) uint64 {
	var ticks uint64
	for _, part := range parts {
		for _, partTrack := range part.Tracks {
			if partTrack.ID == trackID {
				ticks += totalDuration(partTrack.Samples)
			}
		}
	}
	return ticks
}
