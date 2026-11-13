package stream

import (
	"bytes"
	"errors"
	"io"
	"net/http"
	"net/http/httptest"
	"testing"

	"github.com/bluenviron/mediacommon/v2/pkg/codecs/h264"
	"github.com/bluenviron/mediacommon/v2/pkg/formats/mpegts"
	tscodecs "github.com/bluenviron/mediacommon/v2/pkg/formats/mpegts/codecs"
)

// testdata/fmp4 is 6 s of 25 fps H.264 (with B-frames) and 48 kHz AAC as
// fMP4 HLS in three 2 s segments, made by ffmpeg.
const (
	fixtureFrames       = 150
	fixtureFrameTicks   = mpegtsClockRate / 25
	fixtureAudioSamples = 48000 * 6
)

// decodedStream is what an MPEG-TS reader finds in the remuxed output.
type decodedStream struct {
	videoDecodeTimes []int64
	keyframesWithSPS int
	audioFrames      int
}

func decodeTransportStream(t *testing.T, data []byte) decodedStream {
	t.Helper()
	reader := &mpegts.Reader{R: bytes.NewReader(data)}
	if err := reader.Initialize(); err != nil {
		t.Fatalf("not a transport stream: %v", err)
	}
	var decoded decodedStream
	for _, track := range reader.Tracks() {
		switch track.Codec.(type) {
		case *tscodecs.H264:
			reader.OnDataH264(track, func(_ int64, dts int64, accessUnit [][]byte) error {
				decoded.videoDecodeTimes = append(decoded.videoDecodeTimes, dts)
				if h264.IsRandomAccess(accessUnit) && hasH264ParameterSets(accessUnit) {
					decoded.keyframesWithSPS++
				}
				return nil
			})
		case *tscodecs.MPEG4Audio:
			reader.OnDataMPEG4Audio(track, func(_ int64, accessUnits [][]byte) error {
				decoded.audioFrames += len(accessUnits)
				return nil
			})
		}
	}
	for {
		err := reader.Read()
		if errors.Is(err, io.EOF) {
			return decoded
		}
		if err != nil {
			t.Fatalf("reading the transport stream: %v", err)
		}
	}
}

func fmp4CDN(t *testing.T) *httptest.Server {
	t.Helper()
	server := httptest.NewServer(http.FileServer(http.Dir("testdata/fmp4")))
	t.Cleanup(server.Close)
	return server
}

func TestFMP4StreamsReachTheTelevisionAsMPEGTS(t *testing.T) {
	cdn := fmp4CDN(t)
	proxy := NewProxy(newTestCache(t).service, nil, "http://backend.test")
	proxy.kv = proxy.cache.kv

	response := continuousRequest(t, proxy, http.MethodGet, cdn.URL+"/index.m3u8", "0")

	if response.Code != http.StatusOK {
		t.Fatalf("status %d: %s", response.Code, response.Body.String())
	}
	if contentType := response.Header().Get("Content-Type"); contentType != "video/mpeg" {
		t.Fatalf("Content-Type = %q, want MPEG-TS", contentType)
	}
	decoded := decodeTransportStream(t, response.Body.Bytes())
	if len(decoded.videoDecodeTimes) != fixtureFrames {
		t.Fatalf("%d video frames, want %d", len(decoded.videoDecodeTimes), fixtureFrames)
	}
	assertSteadyFrameRate(t, decoded.videoDecodeTimes)
	// One keyframe per 2 s segment, each carrying SPS/PPS for a TV that starts there.
	if decoded.keyframesWithSPS != 3 {
		t.Errorf("%d keyframes with SPS/PPS, want 3", decoded.keyframesWithSPS)
	}
	// AAC frames hold 1024 samples; ffmpeg may pad the last one.
	if want := fixtureAudioSamples / 1024; decoded.audioFrames < want || decoded.audioFrames > want+2 {
		t.Errorf("%d audio frames, want about %d", decoded.audioFrames, want)
	}
}

// assertSteadyFrameRate checks every frame follows the last by one 25 fps frame time.
func assertSteadyFrameRate(t *testing.T, decodeTimes []int64) {
	t.Helper()
	for index := 1; index < len(decodeTimes); index++ {
		step := decodeTimes[index] - decodeTimes[index-1]
		if step != fixtureFrameTicks {
			t.Fatalf("frame %d is %d ticks after the previous one, want %d", index, step, fixtureFrameTicks)
		}
	}
}

func TestARemuxedStreamStartsWhereTheTelevisionSeeked(t *testing.T) {
	cdn := fmp4CDN(t)
	proxy := NewProxy(newTestCache(t).service, nil, "http://backend.test")
	proxy.kv = proxy.cache.kv

	response := continuousRequest(t, proxy, http.MethodGet, cdn.URL+"/index.m3u8", "2")

	decoded := decodeTransportStream(t, response.Body.Bytes())
	if len(decoded.videoDecodeTimes) != fixtureFrames*2/3 {
		t.Fatalf("%d video frames, want the last two segments", len(decoded.videoDecodeTimes))
	}
	if response.Header().Get("X-Otter-Start") != "2.000" {
		t.Errorf("X-Otter-Start = %q", response.Header().Get("X-Otter-Start"))
	}
}

func TestUnreadableFMP4IsSentAsItIs(t *testing.T) {
	if _, err := newTSRemuxer(io.Discard, []byte("not an init segment")); err == nil {
		t.Fatal("a broken init segment was accepted")
	}
	if contentType := continuousContentType(mediaPlaylist{initURL: "https://cdn/init.mp4"}, nil); contentType != "video/mp4" {
		t.Errorf("Content-Type = %q, want the fMP4 passed through", contentType)
	}
}
