package stream

import (
	"context"
	"net/http"
	"net/http/httptest"
	"os"
	"path/filepath"
	"strings"
	"testing"

	"github.com/Letsmoe/otter/internal/plugins"
	"github.com/Letsmoe/otter/internal/storage"
)

func downloadedPlaylist(t *testing.T, cache *testCache, saved Downloaded) []string {
	t.Helper()
	manifestKey, _ := storage.KeyFromURL(saved.URL)
	manifest, err := os.ReadFile(filepath.Join(cache.root, manifestKey))
	if err != nil {
		t.Fatalf("read manifest: %v", err)
	}
	return strings.Split(string(manifest), "\n")
}

func TestDownloadHLSStoresDecryptedSegments(t *testing.T) {
	cdn := fakeCDN(t)
	defer cdn.Close()
	cache := newTestCache(t)
	result := &plugins.ResolveResult{URL: cdn.URL + "/muxed.m3u8", MimeType: "application/x-mpegURL"}

	saved, err := cache.service.Download(context.Background(), result, "https://hoster.example/e/encrypted", nil)
	if err != nil {
		t.Fatalf("download: %v", err)
	}

	var segments []string
	for _, line := range downloadedPlaylist(t, cache, saved) {
		if strings.HasPrefix(line, "#EXT-X-KEY") || strings.HasPrefix(line, "#EXT-X-BYTERANGE") {
			t.Errorf("playlist keeps %q", line)
		}
		if strings.HasPrefix(line, "http") {
			segments = append(segments, line)
		}
	}
	want := []string{"segment-zero", "segment-one", "segment-two", "RANGE"}
	if len(segments) != len(want) {
		t.Fatalf("%d segments, want %d", len(segments), len(want))
	}
	for index, segmentURL := range segments {
		assertBlob(t, cache.root, segmentURL, want[index])
	}
}

func TestDownloadHLSStoresInitSegment(t *testing.T) {
	files := map[string]string{
		"/index.m3u8": "#EXTM3U\n#EXT-X-MAP:URI=\"init.mp4\"\n#EXTINF:4,\nseg0.m4s\n#EXT-X-ENDLIST\n",
		"/init.mp4":   "init-box",
		"/seg0.m4s":   "fragment",
	}
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Write([]byte(files[r.URL.Path]))
	}))
	defer server.Close()
	cache := newTestCache(t)
	result := &plugins.ResolveResult{URL: server.URL + "/index.m3u8", MimeType: "application/x-mpegURL"}

	saved, err := cache.service.Download(context.Background(), result, "https://hoster.example/e/fmp4", nil)
	if err != nil {
		t.Fatalf("download: %v", err)
	}

	manifestKey, _ := storage.KeyFromURL(saved.URL)
	references, err := cache.service.manifestReferences(context.Background(), manifestKey)
	if err != nil {
		t.Fatalf("references: %v", err)
	}
	if len(references) != 2 {
		t.Fatalf("references = %v, want the init segment and one fragment", references)
	}
	assertBlob(t, cache.root, cache.service.storage.DirectURL(references[0]), "init-box")
	assertBlob(t, cache.root, cache.service.storage.DirectURL(references[1]), "fragment")
}
