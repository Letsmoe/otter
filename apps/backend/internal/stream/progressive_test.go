package stream

import (
	"context"
	"io"
	"net/http"
	"net/http/httptest"
	"strconv"
	"strings"
	"sync/atomic"
	"testing"
	"time"

	"github.com/neoworks/otter/internal/plugins"
)

const testSourceURL = "https://hoster.example/e/abc"

// gatedOrigin serves video, holding back everything after the first held
// bytes of a full download until release is closed. Range requests are
// answered at once and counted separately.
type gatedOrigin struct {
	server         *httptest.Server
	release        chan struct{}
	downloads      atomic.Int32
	rangedRequests atomic.Int32
}

func newGatedOrigin(t *testing.T, video string, held int) *gatedOrigin {
	t.Helper()
	origin := &gatedOrigin{release: make(chan struct{})}
	origin.server = httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.Header.Get("Range") != "" {
			origin.rangedRequests.Add(1)
			http.ServeContent(w, r, "movie.mp4", time.Time{}, strings.NewReader(video))
			return
		}
		origin.downloads.Add(1)
		w.Header().Set("Content-Length", strconv.Itoa(len(video)))
		_, _ = io.WriteString(w, video[:held])
		w.(http.Flusher).Flush()
		select {
		case <-origin.release:
		case <-r.Context().Done():
			return
		}
		_, _ = io.WriteString(w, video[held:])
	}))
	t.Cleanup(origin.server.Close)
	t.Cleanup(origin.open)
	return origin
}

func (origin *gatedOrigin) open() {
	select {
	case <-origin.release:
	default:
		close(origin.release)
	}
}

func (origin *gatedOrigin) result(token string) *plugins.ResolveResult {
	return &plugins.ResolveResult{URL: origin.server.URL + "/movie.mp4?token=" + token, MimeType: "video/mp4"}
}

// waitForSize waits until the playback download of testSourceURL stored size bytes.
func waitForSize(t *testing.T, service *CacheService, size int64) *progressiveFile {
	t.Helper()
	deadline := time.Now().Add(5 * time.Second)
	for time.Now().Before(deadline) {
		file, ok := service.progressiveFile(cacheID(testSourceURL))
		if ok && file.storedSize() >= size {
			return file
		}
		time.Sleep(5 * time.Millisecond)
	}
	t.Fatalf("the download never stored %d bytes", size)
	return nil
}

func playbackRequest(t *testing.T, proxy *Proxy, result *plugins.ResolveResult, rangeHeader string) *httptest.ResponseRecorder {
	t.Helper()
	playableURL, err := proxy.ManifestURL(context.Background(), testSourceURL, result)
	if err != nil {
		t.Fatalf("manifest url: %v", err)
	}
	request := httptest.NewRequest(http.MethodGet, strings.TrimPrefix(playableURL, "http://backend.test"), nil)
	if rangeHeader != "" {
		request.Header.Set("Range", rangeHeader)
	}
	recorder := httptest.NewRecorder()
	proxy.ServeHTTP(recorder, request)
	return recorder
}

func TestPlaybackReadsTheGrowingDownload(t *testing.T) {
	video := strings.Repeat("0123456789", 1000)
	origin := newGatedOrigin(t, video, 4000)
	testCache := newTestCache(t)
	proxy := NewProxy(testCache.service, testCache.service.kv, "http://backend.test")

	result := origin.result("first")
	_, _ = proxy.ManifestURL(context.Background(), testSourceURL, result)
	waitForSize(t, testCache.service, 4000)
	go func() {
		time.Sleep(50 * time.Millisecond)
		origin.open()
	}()
	response := playbackRequest(t, proxy, result, "bytes=1000-")

	if response.Code != http.StatusPartialContent || response.Body.String() != video[1000:] {
		t.Fatalf("status %d, body of %d bytes", response.Code, response.Body.Len())
	}
	if got := response.Header().Get("Content-Range"); got != "bytes 1000-9999/10000" {
		t.Errorf("content range = %q", got)
	}
	if origin.downloads.Load() != 1 || origin.rangedRequests.Load() != 0 {
		t.Errorf("origin saw %d downloads and %d range requests, want 1 and 0", origin.downloads.Load(), origin.rangedRequests.Load())
	}
}

func TestOneDownloadPerLinkAcrossResolves(t *testing.T) {
	video := strings.Repeat("0123456789", 1000)
	origin := newGatedOrigin(t, video, 4000)
	origin.open()
	testCache := newTestCache(t)
	proxy := NewProxy(testCache.service, testCache.service.kv, "http://backend.test")

	if _, err := testCache.service.CacheNow(context.Background(), testSourceURL, origin.result("first"), nil, nil); err != nil {
		t.Fatalf("cache: %v", err)
	}
	// A new resolve of the same link has a new token but finds the cached file.
	response := playbackRequest(t, proxy, origin.result("second"), "")
	if response.Code != http.StatusFound {
		t.Fatalf("status = %d, want a redirect to the cached blob", response.Code)
	}
	time.Sleep(50 * time.Millisecond)
	if origin.downloads.Load() != 1 {
		t.Errorf("origin saw %d downloads, want 1", origin.downloads.Load())
	}
	if _, ok := testCache.service.Lookup(context.Background(), testSourceURL); !ok {
		t.Error("lookup by the stored link misses")
	}
}

func TestReadFarAheadOfTheDownloadIsRelayed(t *testing.T) {
	video := strings.Repeat("0123456789", 500_000)
	origin := newGatedOrigin(t, video, 1000)
	testCache := newTestCache(t)
	proxy := NewProxy(testCache.service, testCache.service.kv, "http://backend.test")

	result := origin.result("first")
	_, _ = proxy.ManifestURL(context.Background(), testSourceURL, result)
	waitForSize(t, testCache.service, 1000)
	response := playbackRequest(t, proxy, result, "bytes=4000000-4000009")

	if response.Body.String() != video[4000000:4000010] {
		t.Fatalf("body = %q", response.Body.String())
	}
	if origin.rangedRequests.Load() != 1 {
		t.Errorf("origin saw %d range requests, want 1", origin.rangedRequests.Load())
	}
}

func TestStalledDownloadRelaysTheRest(t *testing.T) {
	previousStall := progressiveStall
	progressiveStall = 100 * time.Millisecond
	t.Cleanup(func() { progressiveStall = previousStall })

	video := strings.Repeat("0123456789", 1000)
	origin := newGatedOrigin(t, video, 4000)
	testCache := newTestCache(t)
	proxy := NewProxy(testCache.service, testCache.service.kv, "http://backend.test")

	result := origin.result("first")
	_, _ = proxy.ManifestURL(context.Background(), testSourceURL, result)
	waitForSize(t, testCache.service, 4000)
	response := playbackRequest(t, proxy, result, "")

	if response.Code != http.StatusOK || response.Body.String() != video {
		t.Fatalf("status %d, body of %d bytes", response.Code, response.Body.Len())
	}
	if origin.rangedRequests.Load() != 1 {
		t.Errorf("origin saw %d range requests, want 1 relay of the rest", origin.rangedRequests.Load())
	}
}

func TestRequestedSpan(t *testing.T) {
	cases := []struct {
		header string
		want   byteSpan
		ok     bool
	}{
		{"", byteSpan{0, 99}, true},
		{"bytes=10-", byteSpan{10, 99}, true},
		{"bytes=10-19", byteSpan{10, 19}, true},
		{"bytes=90-200", byteSpan{90, 99}, true},
		{"bytes=-5", byteSpan{95, 99}, true},
		{"bytes=100-", byteSpan{}, false},
		{"bytes=0-1,5-6", byteSpan{}, false},
		{"bytes=5-1", byteSpan{}, false},
		{"items=0-1", byteSpan{}, false},
	}
	for _, testCase := range cases {
		got, ok := requestedSpan(testCase.header, 100)
		if ok != testCase.ok || got != testCase.want {
			t.Errorf("requestedSpan(%q) = %+v, %v; want %+v, %v", testCase.header, got, ok, testCase.want, testCase.ok)
		}
	}
}
