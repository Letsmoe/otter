package storage

import (
	"context"
	"io"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
)

func TestServedPlaylistHasRelativeBlobURLs(t *testing.T) {
	client, _ := New(t.TempDir(), "http://127.0.0.1:47833")
	playlist := strings.Join([]string{
		"#EXTM3U",
		"#EXTINF:4.0,",
		client.DirectURL("stream-cache/hls/seg0.ts"),
		"#EXTINF:4.0,",
		"https://cdn.example/seg1.ts",
	}, "\n")
	if err := client.Put(context.Background(), "stream-cache/manifest/a.m3u8", strings.NewReader(playlist), int64(len(playlist)), "application/x-mpegURL"); err != nil {
		t.Fatalf("put: %v", err)
	}

	recorder := httptest.NewRecorder()
	client.Handler().ServeHTTP(recorder, httptest.NewRequest(http.MethodGet, "/api/blob/stream-cache/manifest/a.m3u8", nil))
	body, _ := io.ReadAll(recorder.Body)
	if !strings.Contains(string(body), "\n/api/blob/stream-cache/hls/seg0.ts\n") {
		t.Errorf("own segment not root-relative:\n%s", body)
	}
	if !strings.Contains(string(body), "https://cdn.example/seg1.ts") {
		t.Errorf("foreign segment changed:\n%s", body)
	}
}

func TestIsDirectURL(t *testing.T) {
	client, _ := New(t.TempDir(), "http://127.0.0.1:47833/")
	if !client.IsDirectURL(client.DirectURL("a/b.ts")) {
		t.Error("own blob URL not direct")
	}
	if client.IsDirectURL("https://cdn.example/api/blob/a/b.ts") {
		t.Error("foreign URL counted as direct")
	}
}
