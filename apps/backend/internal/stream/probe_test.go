package stream

import (
	"bytes"
	"context"
	"errors"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"

	"github.com/neoworks/otter/internal/plugins"
)

func transportStream(packets int) []byte {
	packet := append([]byte{0x47}, bytes.Repeat([]byte{0xff}, 187)...)
	return bytes.Repeat(packet, packets)
}

func mp4Start() []byte {
	return append([]byte{0x00, 0x00, 0x00, 0x18, 'f', 't', 'y', 'p', 'i', 's', 'o', 'm'}, bytes.Repeat([]byte{0}, 4096)...)
}

func probeServer(t *testing.T, files map[string][]byte) *httptest.Server {
	t.Helper()
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		body, found := files[r.URL.Path]
		if !found {
			http.NotFound(w, r)
			return
		}
		w.Write(body)
	}))
	t.Cleanup(server.Close)
	return server
}

func testProxy(t *testing.T) *Proxy {
	t.Helper()
	proxy := NewProxy(newTestCache(t).service, nil, "http://backend.test")
	proxy.kv = proxy.cache.kv
	return proxy
}

func hlsFiles(segmentName string, segment []byte) map[string][]byte {
	return map[string][]byte{
		"/master.m3u8":    []byte("#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=900000\nlow.m3u8\n#EXT-X-STREAM-INF:BANDWIDTH=3000000\nhigh.m3u8\n"),
		"/high.m3u8":      []byte("#EXTM3U\n#EXTINF:6.0,\n" + segmentName + "\n#EXT-X-ENDLIST\n"),
		"/" + segmentName: segment,
	}
}

func TestProbeAcceptsHLSWithTransportStream(t *testing.T) {
	server := probeServer(t, hlsFiles("seg0.ts", transportStream(50)))
	result, err := testProxy(t).Probe(context.Background(), &plugins.ResolveResult{URL: server.URL + "/master.m3u8", MimeType: "application/x-mpegURL"})
	if err != nil {
		t.Fatal(err)
	}
	if result.BytesPerSecond <= 0 {
		t.Fatalf("speed %v", result.BytesPerSecond)
	}
}

func TestProbeAcceptsSegmentsDisguisedAsHTML(t *testing.T) {
	server := probeServer(t, hlsFiles("page-0.html", transportStream(50)))
	_, err := testProxy(t).Probe(context.Background(), &plugins.ResolveResult{URL: server.URL + "/master.m3u8", MimeType: "application/x-mpegURL"})
	if err != nil {
		t.Fatal(err)
	}
}

func TestProbeRejectsAnErrorPageInsteadOfASegment(t *testing.T) {
	server := probeServer(t, hlsFiles("seg0.ts", []byte("<html><body>Access denied</body></html>")))
	_, err := testProxy(t).Probe(context.Background(), &plugins.ResolveResult{URL: server.URL + "/master.m3u8", MimeType: "application/x-mpegURL"})
	if !errors.Is(err, ErrNotVideo) {
		t.Fatalf("err = %v, want ErrNotVideo", err)
	}
}

func TestProbeRejectsAMissingSegment(t *testing.T) {
	files := hlsFiles("seg0.ts", nil)
	delete(files, "/seg0.ts")
	server := probeServer(t, files)
	_, err := testProxy(t).Probe(context.Background(), &plugins.ResolveResult{URL: server.URL + "/master.m3u8", MimeType: "application/x-mpegURL"})
	if err == nil {
		t.Fatal("a 404 segment passed")
	}
}

func TestProbeChecksFiles(t *testing.T) {
	server := probeServer(t, map[string][]byte{
		"/movie.mp4":  mp4Start(),
		"/movie.webm": append([]byte{0x1a, 0x45, 0xdf, 0xa3}, bytes.Repeat([]byte{1}, 512)...),
		"/fake.mp4":   []byte("<!DOCTYPE html><title>File deleted</title>"),
	})
	proxy := testProxy(t)
	for _, name := range []string{"movie.mp4", "movie.webm"} {
		if _, err := proxy.Probe(context.Background(), &plugins.ResolveResult{URL: server.URL + "/" + name, MimeType: "video/mp4"}); err != nil {
			t.Errorf("%s: %v", name, err)
		}
	}
	_, err := proxy.Probe(context.Background(), &plugins.ResolveResult{URL: server.URL + "/fake.mp4", MimeType: "video/mp4"})
	if !errors.Is(err, ErrNotVideo) {
		t.Fatalf("fake.mp4: err = %v, want ErrNotVideo", err)
	}
}

func TestProbeStopsAtProbeBytesWhenRangeIsIgnored(t *testing.T) {
	big := append(mp4Start(), bytes.Repeat([]byte{7}, 3*probeBytes)...)
	server := probeServer(t, map[string][]byte{"/big.mp4": big})
	body, err := testProxy(t).readStart(context.Background(), server.URL+"/big.mp4", nil)
	if err != nil {
		t.Fatal(err)
	}
	if len(body) != probeBytes {
		t.Fatalf("read %d bytes, want %d", len(body), probeBytes)
	}
	if !strings.HasPrefix(string(body[4:8]), "ftyp") {
		t.Fatal("lost the file start")
	}
}

func TestProbeReadsTheBestResolution(t *testing.T) {
	files := hlsFiles("seg0.ts", transportStream(50))
	files["/master.m3u8"] = []byte("#EXTM3U\n" +
		"#EXT-X-STREAM-INF:BANDWIDTH=900000,RESOLUTION=1280x720,CODECS=\"avc1.4d401f,mp4a.40.2\"\nlow.m3u8\n" +
		"#EXT-X-STREAM-INF:BANDWIDTH=3000000,RESOLUTION=1920x1080\nhigh.m3u8\n")
	server := probeServer(t, files)
	result, err := testProxy(t).Probe(context.Background(), &plugins.ResolveResult{URL: server.URL + "/master.m3u8", MimeType: "application/x-mpegURL"})
	if err != nil {
		t.Fatal(err)
	}
	if result.Quality() != "1080p" {
		t.Fatalf("quality %q, want 1080p", result.Quality())
	}
}
