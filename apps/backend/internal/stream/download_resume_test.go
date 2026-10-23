package stream

import (
	"context"
	"fmt"
	"net/http"
	"net/http/httptest"
	"os"
	"path/filepath"
	"strings"
	"sync/atomic"
	"testing"
	"time"

	"github.com/neoworks/otter/internal/plugins"
	"github.com/neoworks/otter/internal/storage"
)

func TestDownloadMP4ResumesFromPartialFile(t *testing.T) {
	video := strings.Repeat("0123456789", 100)
	var rangeHeader atomic.Value
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		rangeHeader.Store(r.Header.Get("Range"))
		http.ServeContent(w, r, "movie.mp4", time.Time{}, strings.NewReader(video))
	}))
	defer server.Close()

	cache := newTestCache(t)
	resumeKey := "https://hoster.example/e/abc"
	key := blobPrefix + "mp4/" + urlHash("download:"+resumeKey) + ".mp4"
	writePartial(t, cache.root, key, video[:400])

	result := &plugins.ResolveResult{URL: server.URL + "/movie.mp4?token=new", MimeType: "video/mp4"}
	saved, err := cache.service.Download(context.Background(), result, resumeKey, nil)
	if err != nil {
		t.Fatalf("download: %v", err)
	}
	if got := rangeHeader.Load(); got != "bytes=400-" {
		t.Fatalf("range header = %q, want bytes=400-", got)
	}
	assertBlob(t, cache.root, saved.URL, video)
}

func TestDownloadHLSKeepsSegmentsAcrossResolves(t *testing.T) {
	var segmentRequests atomic.Int32
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if strings.HasSuffix(r.URL.Path, ".m3u8") {
			token := r.URL.Query().Get("token")
			fmt.Fprintf(w, "#EXTM3U\n#EXTINF:6,\nseg0.ts?token=%s\n#EXTINF:6,\nseg1.ts?token=%s\n#EXT-X-ENDLIST\n", token, token)
			return
		}
		segmentRequests.Add(1)
		fmt.Fprint(w, r.URL.Path)
	}))
	defer server.Close()

	cache := newTestCache(t)
	resumeKey := "https://hoster.example/e/abc"
	download := func(token string) Downloaded {
		result := &plugins.ResolveResult{URL: server.URL + "/index.m3u8?token=" + token, MimeType: "application/x-mpegURL"}
		saved, err := cache.service.Download(context.Background(), result, resumeKey, nil)
		if err != nil {
			t.Fatalf("download: %v", err)
		}
		return saved
	}

	download("first")
	saved := download("second")
	if got := segmentRequests.Load(); got != 2 {
		t.Fatalf("segment requests = %d, want 2 (second run reuses both)", got)
	}
	manifestKey, _ := storage.KeyFromURL(saved.URL)
	manifest, err := os.ReadFile(filepath.Join(cache.root, manifestKey))
	if err != nil {
		t.Fatalf("read manifest: %v", err)
	}
	if strings.Count(string(manifest), storage.RoutePrefix) != 2 {
		t.Fatalf("manifest does not point at two blobs:\n%s", manifest)
	}
}

func writePartial(t *testing.T, root, key, content string) {
	t.Helper()
	target := filepath.Join(root, key+".partial")
	if err := os.MkdirAll(filepath.Dir(target), 0o755); err != nil {
		t.Fatal(err)
	}
	if err := os.WriteFile(target, []byte(content), 0o644); err != nil {
		t.Fatal(err)
	}
}

func assertBlob(t *testing.T, root, blobURL, want string) {
	t.Helper()
	key, ok := storage.KeyFromURL(blobURL)
	if !ok {
		t.Fatalf("not a blob url: %s", blobURL)
	}
	got, err := os.ReadFile(filepath.Join(root, key))
	if err != nil {
		t.Fatalf("read blob: %v", err)
	}
	if string(got) != want {
		t.Fatalf("blob has %d bytes, want %d", len(got), len(want))
	}
}
