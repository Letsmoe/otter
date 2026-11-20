package stream

import (
	"bytes"
	"errors"
	"io"
	"net/http"
	"net/http/httptest"
	"os"
	"testing"
)

// fixtureFragmentedMP4 is testdata/fmp4 as one file, the way the phone's encoder writes it.
func fixtureFragmentedMP4(t *testing.T) []byte {
	t.Helper()
	var joined []byte
	for _, name := range []string{"init.mp4", "seg0.m4s", "seg1.m4s", "seg2.m4s"} {
		part, err := os.ReadFile("testdata/fmp4/" + name)
		if err != nil {
			t.Fatal(err)
		}
		joined = append(joined, part...)
	}
	return joined
}

// overlayEncoder stands in for the app's loopback encoder; it counts the streams it was asked for.
func overlayEncoder(t *testing.T, body []byte, requests *int) *httptest.Server {
	t.Helper()
	server := httptest.NewServer(http.HandlerFunc(func(writer http.ResponseWriter, request *http.Request) {
		*requests++
		if request.URL.Path != "/overlay/job-1" {
			http.NotFound(writer, request)
			return
		}
		writer.Write(body)
	}))
	t.Cleanup(server.Close)
	return server
}

func overlayRequest(proxy *Proxy, method string, job string) *httptest.ResponseRecorder {
	request := httptest.NewRequest(method, "/api/stream/overlay?job="+job, nil)
	response := httptest.NewRecorder()
	proxy.ServeHTTP(response, request)
	return response
}

func TestOverlayStreamsReachTheTelevisionAsMPEGTS(t *testing.T) {
	requests := 0
	encoder := overlayEncoder(t, fixtureFragmentedMP4(t), &requests)
	proxy := NewProxy(nil, nil, "http://backend.test")
	proxy.SetOverlaySource(encoder.URL + "/")

	response := overlayRequest(proxy, http.MethodGet, "job-1")

	if response.Code != http.StatusOK {
		t.Fatalf("status %d: %s", response.Code, response.Body.String())
	}
	if contentType := response.Header().Get("Content-Type"); contentType != overlayContentType {
		t.Fatalf("Content-Type %q", contentType)
	}
	decoded := decodeTransportStream(t, response.Body.Bytes())
	if len(decoded.videoDecodeTimes) != fixtureFrames {
		t.Fatalf("%d video frames, want %d", len(decoded.videoDecodeTimes), fixtureFrames)
	}
	if decoded.keyframesWithSPS == 0 {
		t.Fatal("no keyframe carries its SPS/PPS")
	}
	if decoded.audioFrames == 0 {
		t.Fatal("no audio")
	}
}

// testdata/media3-overlay.mp4 is 4 s of 10 fps H.264 and AAC as the app's encoder writes it
// (OverlayEncoderTest's overlay-small.mp4): fragments without tfdt, with absolute data offsets.
func TestMedia3FragmentsReachTheTelevisionAsMPEGTS(t *testing.T) {
	sample, err := os.ReadFile("testdata/media3-overlay.mp4")
	if err != nil {
		t.Fatal(err)
	}
	requests := 0
	encoder := overlayEncoder(t, sample, &requests)
	proxy := NewProxy(nil, nil, "http://backend.test")
	proxy.SetOverlaySource(encoder.URL)

	response := overlayRequest(proxy, http.MethodGet, "job-1")

	if response.Code != http.StatusOK {
		t.Fatalf("status %d: %s", response.Code, response.Body.String())
	}
	decoded := decodeTransportStream(t, response.Body.Bytes())
	if len(decoded.videoDecodeTimes) != 40 {
		t.Fatalf("%d video frames, want 40", len(decoded.videoDecodeTimes))
	}
	assertSteadyDecodeTimes(t, decoded.videoDecodeTimes, mpegtsClockRate/10)
	if decoded.keyframesWithSPS < 4 || decoded.audioFrames == 0 {
		t.Fatalf("%d keyframes with SPS, %d audio frames", decoded.keyframesWithSPS, decoded.audioFrames)
	}
}

// assertSteadyDecodeTimes checks frames follow each other by one frame time across fragments,
// which the summed-up decode times have to get right.
func assertSteadyDecodeTimes(t *testing.T, decodeTimes []int64, frameTicks int64) {
	t.Helper()
	for index := 1; index < len(decodeTimes); index++ {
		step := decodeTimes[index] - decodeTimes[index-1]
		if step < frameTicks-1 || step > frameTicks+1 {
			t.Fatalf("frame %d follows the last by %d ticks, want %d", index, step, frameTicks)
		}
	}
}

// OVERLAY_SAMPLE names a file the app's encoder wrote (OverlayEncoderTest's overlay-output.mp4,
// pulled with adb). The TV's MPEG-TS lands next to it as <file>.ts, to look at with ffmpeg.
func TestThePhonesEncoderOutputReachesTheTelevision(t *testing.T) {
	samplePath := os.Getenv("OVERLAY_SAMPLE")
	if samplePath == "" {
		t.Skip("OVERLAY_SAMPLE not set")
	}
	sample, err := os.ReadFile(samplePath)
	if err != nil {
		t.Fatal(err)
	}
	requests := 0
	encoder := overlayEncoder(t, sample, &requests)
	proxy := NewProxy(nil, nil, "http://backend.test")
	proxy.SetOverlaySource(encoder.URL)

	response := overlayRequest(proxy, http.MethodGet, "job-1")

	if response.Code != http.StatusOK {
		t.Fatalf("status %d: %s", response.Code, response.Body.String())
	}
	decoded := decodeTransportStream(t, response.Body.Bytes())
	if len(decoded.videoDecodeTimes) == 0 || decoded.keyframesWithSPS == 0 || decoded.audioFrames == 0 {
		t.Fatalf("decoded %d frames, %d keyframes with SPS, %d audio frames", len(decoded.videoDecodeTimes), decoded.keyframesWithSPS, decoded.audioFrames)
	}
	t.Logf("%d video frames, %d keyframes with SPS, %d audio frames", len(decoded.videoDecodeTimes), decoded.keyframesWithSPS, decoded.audioFrames)
	if err := os.WriteFile(samplePath+".ts", response.Body.Bytes(), 0o644); err != nil {
		t.Fatal(err)
	}
}

func TestOverlayHeadIsAnsweredWithoutTheEncoder(t *testing.T) {
	requests := 0
	encoder := overlayEncoder(t, fixtureFragmentedMP4(t), &requests)
	proxy := NewProxy(nil, nil, "http://backend.test")
	proxy.SetOverlaySource(encoder.URL)

	response := overlayRequest(proxy, http.MethodHead, "job-1")

	if response.Code != http.StatusOK || response.Header().Get("Content-Type") != overlayContentType {
		t.Fatalf("status %d, Content-Type %q", response.Code, response.Header().Get("Content-Type"))
	}
	if requests != 0 {
		t.Fatalf("the encoder was asked %d times for a HEAD", requests)
	}
}

func TestOverlayFailsWhenTheEncoderHasNoSuchJob(t *testing.T) {
	requests := 0
	encoder := overlayEncoder(t, fixtureFragmentedMP4(t), &requests)
	proxy := NewProxy(nil, nil, "http://backend.test")
	proxy.SetOverlaySource(encoder.URL)

	response := overlayRequest(proxy, http.MethodGet, "job-2")

	if response.Code != http.StatusBadGateway {
		t.Fatalf("status %d, want 502", response.Code)
	}
}

func TestOverlayIsMissingWithoutAnEncoder(t *testing.T) {
	proxy := NewProxy(nil, nil, "http://backend.test")

	if response := overlayRequest(proxy, http.MethodGet, "job-1"); response.Code != http.StatusNotFound {
		t.Fatalf("status %d, want 404", response.Code)
	}
	if response := overlayRequest(proxy, http.MethodGet, "../etc"); response.Code != http.StatusNotFound {
		t.Fatalf("status %d for a bad job without an encoder, want 404", response.Code)
	}
}

func TestOverlayRejectsJobsThatAreNoIDs(t *testing.T) {
	proxy := NewProxy(nil, nil, "http://backend.test")
	proxy.SetOverlaySource("http://127.0.0.1:1")

	if response := overlayRequest(proxy, http.MethodGet, "..%2Fsecret"); response.Code != http.StatusBadRequest {
		t.Fatalf("status %d, want 400", response.Code)
	}
}

func TestBoxesSplitIntoInitAndFragments(t *testing.T) {
	reader := bytes.NewReader(fixtureFragmentedMP4(t))

	initSegment, err := readOverlayInit(reader)
	if err != nil {
		t.Fatal(err)
	}
	if _, err := newTSRemuxer(io.Discard, initSegment); err != nil {
		t.Fatalf("init segment unreadable: %v", err)
	}
	fragments := 0
	for {
		_, err := readOverlayFragment(reader)
		if errors.Is(err, io.EOF) {
			break
		}
		if err != nil {
			t.Fatal(err)
		}
		fragments++
	}
	if fragments < 3 {
		t.Fatalf("%d fragments, want at least 3", fragments)
	}
}

func TestATruncatedFragmentIsAnError(t *testing.T) {
	stream := fixtureFragmentedMP4(t)
	reader := bytes.NewReader(stream[:len(stream)-100])
	if _, err := readOverlayInit(reader); err != nil {
		t.Fatal(err)
	}
	var lastErr error
	for lastErr == nil {
		_, lastErr = readOverlayFragment(reader)
	}
	if !errors.Is(lastErr, io.ErrUnexpectedEOF) {
		t.Fatalf("error %v, want unexpected EOF", lastErr)
	}
}
