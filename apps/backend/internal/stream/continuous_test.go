package stream

import (
	"bytes"
	"crypto/aes"
	"crypto/cipher"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
	"time"
)

var testKey = []byte("0123456789abcdef")

func encryptSegment(t *testing.T, plain []byte, iv []byte) []byte {
	t.Helper()
	padding := aes.BlockSize - len(plain)%aes.BlockSize
	padded := append(append([]byte{}, plain...), bytes.Repeat([]byte{byte(padding)}, padding)...)
	block, err := aes.NewCipher(testKey)
	if err != nil {
		t.Fatal(err)
	}
	encrypted := make([]byte, len(padded))
	cipher.NewCBCEncrypter(block, iv).CryptBlocks(encrypted, padded)
	return encrypted
}

// fakeCDN serves a master playlist whose best variant needs a separate audio
// playlist, and a muxed variant with AES-128 and byte-range segments.
func fakeCDN(t *testing.T) *httptest.Server {
	t.Helper()
	explicitIV := []byte("fedcba9876543210")
	files := map[string][]byte{
		"/master.m3u8": []byte(strings.Join([]string{
			"#EXTM3U",
			`#EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID="aud",URI="audio.m3u8"`,
			`#EXT-X-STREAM-INF:BANDWIDTH=9000000,AUDIO="aud"`,
			"video-only.m3u8",
			"#EXT-X-STREAM-INF:BANDWIDTH=3000000",
			"muxed.m3u8",
			"#EXT-X-STREAM-INF:BANDWIDTH=800000",
			"low.m3u8",
		}, "\n")),
		"/muxed.m3u8": []byte(strings.Join([]string{
			"#EXTM3U",
			"#EXT-X-MEDIA-SEQUENCE:7",
			`#EXT-X-KEY:METHOD=AES-128,URI="key.bin"`,
			"#EXTINF:4.0,",
			"seg0.ts",
			"#EXTINF:4.0,",
			"seg1.ts",
			`#EXT-X-KEY:METHOD=AES-128,URI="key.bin",IV=0x` + "66656463626139383736353433323130",
			"#EXTINF:4.0,",
			"seg2.ts",
			"#EXT-X-KEY:METHOD=NONE",
			"#EXTINF:2.5,",
			"#EXT-X-BYTERANGE:5@3",
			"ranged.ts",
			"#EXT-X-ENDLIST",
		}, "\n")),
		"/key.bin":   testKey,
		"/seg0.ts":   encryptSegment(t, []byte("segment-zero"), sequenceIV(7)),
		"/seg1.ts":   encryptSegment(t, []byte("segment-one"), sequenceIV(8)),
		"/seg2.ts":   encryptSegment(t, []byte("segment-two"), explicitIV),
		"/ranged.ts": []byte("xxxRANGEyyy"),
	}
	return httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		body, ok := files[r.URL.Path]
		if !ok {
			http.NotFound(w, r)
			return
		}
		http.ServeContent(w, r, r.URL.Path, time.Time{}, bytes.NewReader(body))
	}))
}

func continuousRequest(t *testing.T, proxy *Proxy, method string, manifestURL string, start string) *httptest.ResponseRecorder {
	t.Helper()
	request := httptest.NewRequest(method, "/api/stream/continuous?s=none&u="+encURL(manifestURL)+"&start="+start, nil)
	recorder := httptest.NewRecorder()
	proxy.ServeHTTP(recorder, request)
	return recorder
}

func TestContinuousJoinsDecryptedSegments(t *testing.T) {
	cdn := fakeCDN(t)
	defer cdn.Close()
	proxy := NewProxy(newTestCache(t).service, nil, "http://backend.test")
	proxy.kv = proxy.cache.kv

	response := continuousRequest(t, proxy, http.MethodGet, cdn.URL+"/master.m3u8", "0")

	if response.Code != http.StatusOK {
		t.Fatalf("status %d: %s", response.Code, response.Body.String())
	}
	if want := "segment-zerosegment-onesegment-twoRANGE"; response.Body.String() != want {
		t.Fatalf("body = %q, want %q", response.Body.String(), want)
	}
	if contentType := response.Header().Get("Content-Type"); contentType != "video/mpeg" {
		t.Errorf("Content-Type = %q", contentType)
	}
	if duration := response.Header().Get("X-Otter-Duration"); duration != "14.500" {
		t.Errorf("X-Otter-Duration = %q", duration)
	}
}

func TestContinuousStartsAtSegmentBoundary(t *testing.T) {
	cdn := fakeCDN(t)
	defer cdn.Close()
	proxy := NewProxy(newTestCache(t).service, nil, "http://backend.test")
	proxy.kv = proxy.cache.kv

	response := continuousRequest(t, proxy, http.MethodGet, cdn.URL+"/master.m3u8", "9.5")

	if want := "segment-twoRANGE"; response.Body.String() != want {
		t.Fatalf("body = %q, want %q", response.Body.String(), want)
	}
	if start := response.Header().Get("X-Otter-Start"); start != "8.000" {
		t.Errorf("X-Otter-Start = %q, want 8.000", start)
	}
}

func TestContinuousHeadSendsNoBody(t *testing.T) {
	cdn := fakeCDN(t)
	defer cdn.Close()
	proxy := NewProxy(newTestCache(t).service, nil, "http://backend.test")
	proxy.kv = proxy.cache.kv

	response := continuousRequest(t, proxy, http.MethodHead, cdn.URL+"/master.m3u8", "0")

	if response.Body.Len() != 0 || response.Header().Get("X-Otter-Duration") == "" {
		t.Fatalf("HEAD: body %d bytes, headers %v", response.Body.Len(), response.Header())
	}
}

func TestContinuousRefusesSampleAES(t *testing.T) {
	_, err := parseMediaPlaylist("#EXT-X-KEY:METHOD=SAMPLE-AES,URI=\"k\"\n#EXTINF:4,\na.ts", nil)
	if err == nil {
		t.Fatal("SAMPLE-AES accepted")
	}
}

func TestContinuousRetriesFailedSegment(t *testing.T) {
	previousDelays := segmentRetryDelays
	segmentRetryDelays = []time.Duration{time.Millisecond}
	defer func() { segmentRetryDelays = previousDelays }()

	failedOnce := false
	cdn := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		switch r.URL.Path {
		case "/media.m3u8":
			w.Write([]byte("#EXTM3U\n#EXTINF:4.0,\nflaky.ts\n#EXT-X-ENDLIST"))
		case "/flaky.ts":
			if !failedOnce {
				failedOnce = true
				http.Error(w, "busy", http.StatusBadGateway)
				return
			}
			w.Write([]byte("segment-after-retry"))
		default:
			http.NotFound(w, r)
		}
	}))
	defer cdn.Close()
	proxy := NewProxy(newTestCache(t).service, nil, "http://backend.test")
	proxy.kv = proxy.cache.kv

	response := continuousRequest(t, proxy, http.MethodGet, cdn.URL+"/media.m3u8", "0")

	if want := "segment-after-retry"; response.Body.String() != want {
		t.Fatalf("body = %q, want %q", response.Body.String(), want)
	}
}
