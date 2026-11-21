package stream

import (
	"bytes"
	"errors"
	"io"
	"net/http"
	"net/http/httptest"
	"os"
	"strconv"
	"sync"
	"testing"
	"time"

	"github.com/bluenviron/mediacommon/v2/pkg/codecs/h264"
	"github.com/bluenviron/mediacommon/v2/pkg/formats/mpegts"
	tscodecs "github.com/bluenviron/mediacommon/v2/pkg/formats/mpegts/codecs"
)

// testdata/media3-overlay.mp4 holds 40 frames at 10 fps.
const (
	media3SampleFrames     = 40
	media3SampleFrameTicks = mpegtsClockRate / 10
)

// sessionPhone stands in for the app's loopback server: it hands out the given jobs in order,
// then says the session is over. It records the at= of every request. With a bridge it serves
// that as the session's bridge clip; firstJobDelay holds the first job's answer back that long.
type sessionPhone struct {
	jobs          [][]byte
	bridge        []byte
	firstJobDelay time.Duration
	mutex         sync.Mutex
	asked         []int64
	started       map[string]int64
}

func (phone *sessionPhone) serve(t *testing.T) *httptest.Server {
	t.Helper()
	server := httptest.NewServer(http.HandlerFunc(func(writer http.ResponseWriter, request *http.Request) {
		switch request.URL.Path {
		case "/session/tv-1/next":
			phone.serveNext(writer, request)
		case "/session/tv-1/bridge":
			phone.serveBridge(writer, request)
		case "/session/tv-1/started":
			phone.recordStarted(request)
			writer.WriteHeader(http.StatusNoContent)
		default:
			http.NotFound(writer, request)
		}
	}))
	t.Cleanup(server.Close)
	return server
}

func (phone *sessionPhone) serveNext(writer http.ResponseWriter, request *http.Request) {
	at, err := strconv.ParseInt(request.URL.Query().Get("at"), 10, 64)
	if err != nil {
		http.Error(writer, "bad at", http.StatusBadRequest)
		return
	}
	job, number := phone.next(at)
	if job == nil {
		http.NotFound(writer, request)
		return
	}
	writer.Header().Set(sessionJobHeader, "job-"+strconv.Itoa(number))
	writer.WriteHeader(http.StatusOK)
	if number == 1 {
		time.Sleep(phone.firstJobDelay)
	}
	writer.Write(job)
}

func (phone *sessionPhone) serveBridge(writer http.ResponseWriter, request *http.Request) {
	if phone.bridge == nil {
		http.NotFound(writer, request)
		return
	}
	writer.Write(phone.bridge)
}

func (phone *sessionPhone) recordStarted(request *http.Request) {
	at, _ := strconv.ParseInt(request.URL.Query().Get("at"), 10, 64)
	phone.mutex.Lock()
	defer phone.mutex.Unlock()
	if phone.started == nil {
		phone.started = map[string]int64{}
	}
	phone.started[request.URL.Query().Get("job")] = at
}

// startedAt waits a moment for the backend's report of where a job started; false when none came.
func (phone *sessionPhone) startedAt(jobID string) (int64, bool) {
	for attempt := 0; attempt < 100; attempt++ {
		phone.mutex.Lock()
		at, found := phone.started[jobID]
		phone.mutex.Unlock()
		if found {
			return at, true
		}
		time.Sleep(10 * time.Millisecond)
	}
	return 0, false
}

// next hands out the jobs in order with their number, counted from 1; nil once they ran out.
func (phone *sessionPhone) next(at int64) ([]byte, int) {
	phone.mutex.Lock()
	defer phone.mutex.Unlock()
	phone.asked = append(phone.asked, at)
	number := len(phone.asked)
	if number > len(phone.jobs) {
		return nil, number
	}
	return phone.jobs[number-1], number
}

// withBridgeAfter shortens how long a job may be silent before the TV gets the bridge, for one test.
func withBridgeAfter(t *testing.T, wait time.Duration) {
	t.Helper()
	previous := bridgeAfter
	bridgeAfter = wait
	t.Cleanup(func() { bridgeAfter = previous })
}

func media3Sample(t *testing.T) []byte {
	t.Helper()
	sample, err := os.ReadFile("testdata/media3-overlay.mp4")
	if err != nil {
		t.Fatal(err)
	}
	return sample
}

func sessionRequest(t *testing.T, phone *sessionPhone, method string) *httptest.ResponseRecorder {
	t.Helper()
	proxy := NewProxy(nil, nil, "http://backend.test")
	proxy.SetOverlaySource(phone.serve(t).URL)
	request := httptest.NewRequest(method, "/api/stream/tv?session=tv-1", nil)
	response := httptest.NewRecorder()
	proxy.ServeHTTP(response, request)
	return response
}

// sessionVideo is the decode time of every frame of the session and the SPS each keyframe carries.
type sessionVideo struct {
	decodeTimes []int64
	keyframeSPS [][]byte
}

func decodeSessionVideo(t *testing.T, data []byte) sessionVideo {
	t.Helper()
	reader := &mpegts.Reader{R: bytes.NewReader(data)}
	if err := reader.Initialize(); err != nil {
		t.Fatalf("not a transport stream: %v", err)
	}
	var video sessionVideo
	for _, track := range reader.Tracks() {
		if _, isVideo := track.Codec.(*tscodecs.H264); !isVideo {
			continue
		}
		reader.OnDataH264(track, func(_ int64, dts int64, accessUnit [][]byte) error {
			video.decodeTimes = append(video.decodeTimes, dts)
			if h264.IsRandomAccess(accessUnit) {
				video.keyframeSPS = append(video.keyframeSPS, spsOf(accessUnit))
			}
			return nil
		})
	}
	for {
		err := reader.Read()
		if errors.Is(err, io.EOF) {
			return video
		}
		if err != nil {
			t.Fatalf("reading the transport stream: %v", err)
		}
	}
}

func spsOf(accessUnit [][]byte) []byte {
	for _, unit := range accessUnit {
		if len(unit) > 0 && h264.NALUType(unit[0]&0x1F) == h264.NALUTypeSPS {
			return unit
		}
	}
	return nil
}

// assertRisingDecodeTimes checks every frame follows the last by at least one frame time.
func assertRisingDecodeTimes(t *testing.T, decodeTimes []int64, frameTicks int64) {
	t.Helper()
	for index := 1; index < len(decodeTimes); index++ {
		step := decodeTimes[index] - decodeTimes[index-1]
		if step < frameTicks-1 {
			t.Fatalf("frame %d follows the last by %d ticks, want at least %d", index, step, frameTicks)
		}
	}
}

func TestTwoJobsBecomeOneStreamWithRisingTimestamps(t *testing.T) {
	sample := media3Sample(t)
	phone := &sessionPhone{jobs: [][]byte{sample, sample}}

	response := sessionRequest(t, phone, http.MethodGet)

	if response.Code != http.StatusOK {
		t.Fatalf("status %d: %s", response.Code, response.Body.String())
	}
	video := decodeSessionVideo(t, response.Body.Bytes())
	if len(video.decodeTimes) != 2*media3SampleFrames {
		t.Fatalf("%d video frames, want %d", len(video.decodeTimes), 2*media3SampleFrames)
	}
	assertSteadyDecodeTimes(t, video.decodeTimes[:media3SampleFrames], media3SampleFrameTicks)
	assertSteadyDecodeTimes(t, video.decodeTimes[media3SampleFrames:], media3SampleFrameTicks)
	assertRisingDecodeTimes(t, video.decodeTimes, media3SampleFrameTicks)
	if video.decodeTimes[0] != timestampOffset {
		t.Errorf("the first frame decodes at %d, want %d", video.decodeTimes[0], timestampOffset)
	}
	if len(phone.asked) != 3 || phone.asked[0] != 0 {
		t.Fatalf("the phone was asked at %v, want three times from 0", phone.asked)
	}
	// The second job starts where the first one's samples end (its audio runs a little longer than its video).
	secondJobTicks := video.decodeTimes[media3SampleFrames] - timestampOffset
	if secondJobTicks != phone.asked[1]*mpegtsClockRate/1_000_000 {
		t.Errorf("the second job starts at %d ticks, the phone was told %d µs", secondJobTicks, phone.asked[1])
	}
	if phone.asked[1] < 3_900_000 || phone.asked[1] > 4_500_000 || phone.asked[2] <= phone.asked[1] {
		t.Errorf("jobs were asked for at %v µs", phone.asked)
	}
}

func TestEachJobsKeyframesCarryItsOwnParameterSets(t *testing.T) {
	sample := media3Sample(t)
	changed := withChangedSPS(t, sample)
	phone := &sessionPhone{jobs: [][]byte{sample, changed}}

	response := sessionRequest(t, phone, http.MethodGet)

	video := decodeSessionVideo(t, response.Body.Bytes())
	if len(video.keyframeSPS) < 2 {
		t.Fatalf("%d keyframes", len(video.keyframeSPS))
	}
	first := video.keyframeSPS[0]
	last := video.keyframeSPS[len(video.keyframeSPS)-1]
	if first == nil || last == nil {
		t.Fatal("a keyframe carries no SPS")
	}
	if bytes.Equal(first, last) {
		t.Fatal("the second job's keyframes carry the first job's SPS")
	}
	if last[3] != first[3]^0x01 {
		t.Errorf("the second job's SPS has level %d, want %d", last[3], first[3]^0x01)
	}
}

func TestACutJobIsFollowedByTheNextOne(t *testing.T) {
	sample := media3Sample(t)
	// Cut inside the last fragment, the way a cancelled encoder leaves it.
	cut := sample[:len(sample)-200]
	phone := &sessionPhone{jobs: [][]byte{cut, sample}}

	response := sessionRequest(t, phone, http.MethodGet)

	video := decodeSessionVideo(t, response.Body.Bytes())
	frames := len(video.decodeTimes)
	if frames <= media3SampleFrames || frames >= 2*media3SampleFrames {
		t.Fatalf("%d video frames, want the cut job's whole fragments and all of the second", frames)
	}
	assertRisingDecodeTimes(t, video.decodeTimes, media3SampleFrameTicks)
}

func TestAJobWithoutVideoIsSkipped(t *testing.T) {
	sample := media3Sample(t)
	phone := &sessionPhone{jobs: [][]byte{[]byte("not an fMP4 stream"), sample}}

	response := sessionRequest(t, phone, http.MethodGet)

	video := decodeSessionVideo(t, response.Body.Bytes())
	if len(video.decodeTimes) != media3SampleFrames {
		t.Fatalf("%d video frames, want the good job's %d", len(video.decodeTimes), media3SampleFrames)
	}
}

func TestAnEndedSessionIsMissing(t *testing.T) {
	phone := &sessionPhone{}

	response := sessionRequest(t, phone, http.MethodGet)

	if response.Code != http.StatusNotFound {
		t.Fatalf("status %d, want 404", response.Code)
	}
}

func TestSessionHeadIsAnsweredWithoutThePhone(t *testing.T) {
	phone := &sessionPhone{}

	response := sessionRequest(t, phone, http.MethodHead)

	if response.Code != http.StatusOK || response.Header().Get("Content-Type") != overlayContentType {
		t.Fatalf("status %d, Content-Type %q", response.Code, response.Header().Get("Content-Type"))
	}
	if len(phone.asked) != 0 {
		t.Fatalf("the phone was asked %d times for a HEAD", len(phone.asked))
	}
}

// withChangedSPS returns the stream with the SPS in its init segment one level apart.
func withChangedSPS(t *testing.T, stream []byte) []byte {
	t.Helper()
	changed := append([]byte{}, stream...)
	initSegment, err := readOverlayInit(bytes.NewReader(stream))
	if err != nil {
		t.Fatal(err)
	}
	avcC := bytes.Index(initSegment, []byte("avcC"))
	if avcC < 0 {
		t.Fatal("no avcC box")
	}
	// avcC payload: version, profile, compatibility, level, length size, SPS count, SPS length (2), SPS.
	spsStart := avcC + 4 + 8
	changed[spsStart+3] ^= 0x01
	// The avcC's own copy of the level follows the SPS's.
	changed[avcC+4+3] ^= 0x01
	return changed
}

func TestASlowJobIsBridgedAndTheBridgeMovesItsStart(t *testing.T) {
	withBridgeAfter(t, 50*time.Millisecond)
	sample := media3Sample(t)
	phone := &sessionPhone{jobs: [][]byte{sample}, bridge: sample, firstJobDelay: 400 * time.Millisecond}

	response := sessionRequest(t, phone, http.MethodGet)

	video := decodeSessionVideo(t, response.Body.Bytes())
	if len(video.decodeTimes) != 2*media3SampleFrames {
		t.Fatalf("%d video frames, want one bridge and the job, %d", len(video.decodeTimes), 2*media3SampleFrames)
	}
	assertRisingDecodeTimes(t, video.decodeTimes, media3SampleFrameTicks)
	started, found := phone.startedAt("job-1")
	if !found {
		t.Fatal("the phone wasn't told where the job started")
	}
	jobTicks := video.decodeTimes[media3SampleFrames] - timestampOffset
	if started != jobTicks*1_000_000/mpegtsClockRate {
		t.Errorf("the phone was told the job starts at %d µs, its first frame is at %d ticks", started, jobTicks)
	}
	if phone.asked[0] != 0 || started < 3_900_000 {
		t.Errorf("asked at %d µs, started at %d µs: the bridge should come first", phone.asked[0], started)
	}
}

func TestQuickJobsAreNotBridged(t *testing.T) {
	sample := media3Sample(t)
	phone := &sessionPhone{jobs: [][]byte{sample, sample}, bridge: sample}

	response := sessionRequest(t, phone, http.MethodGet)

	video := decodeSessionVideo(t, response.Body.Bytes())
	if len(video.decodeTimes) != 2*media3SampleFrames {
		t.Fatalf("%d video frames, want just the two jobs' %d", len(video.decodeTimes), 2*media3SampleFrames)
	}
	if _, found := phone.startedAt("job-2"); !found {
		t.Error("the phone wasn't told where the second job started")
	}
}

func TestTheBridgeClipIsSplitIntoItsFragments(t *testing.T) {
	clip, err := parseBridgeClip(media3Sample(t))

	if err != nil {
		t.Fatal(err)
	}
	if len(clip.fragments) == 0 {
		t.Fatal("no fragments")
	}
	if clip.duration != 4*time.Second {
		t.Errorf("the clip lasts %s, want its 40 frames at 10 fps, 4s", clip.duration)
	}
}
