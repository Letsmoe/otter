package storage

import (
	"context"
	"io"
	"net/http"
	"net/http/httptest"
	"os"
	"path/filepath"
	"strings"
	"testing"
	"time"
)

func TestPutServeRange(t *testing.T) {
	ctx := context.Background()
	client, err := New(t.TempDir(), "http://example.test/")
	if err != nil {
		t.Fatalf("new: %v", err)
	}
	if err := client.Put(ctx, "stream-cache/mp4/abc.mp4", strings.NewReader("0123456789"), 10, "video/mp4"); err != nil {
		t.Fatalf("put: %v", err)
	}
	if !client.Exists(ctx, "stream-cache/mp4/abc.mp4") {
		t.Fatal("exists = false after put")
	}
	if got := client.DirectURL("a/b.png"); got != "http://example.test/api/blob/a/b.png" {
		t.Errorf("direct url = %q", got)
	}

	request := httptest.NewRequest(http.MethodGet, "/api/blob/stream-cache/mp4/abc.mp4", nil)
	request.Header.Set("Range", "bytes=2-4")
	recorder := httptest.NewRecorder()
	client.Handler().ServeHTTP(recorder, request)
	body, _ := io.ReadAll(recorder.Body)
	if recorder.Code != http.StatusPartialContent || string(body) != "234" {
		t.Errorf("range response = %d %q", recorder.Code, body)
	}
	if contentType := recorder.Header().Get("Content-Type"); contentType != "video/mp4" {
		t.Errorf("content type = %q", contentType)
	}
}

func TestKeysStayInsideRoot(t *testing.T) {
	parent := t.TempDir()
	root := filepath.Join(parent, "blobs")
	client, _ := New(root, "http://example.test")
	if err := client.Put(context.Background(), "../../outside", strings.NewReader("x"), 1, ""); err != nil {
		t.Fatalf("put: %v", err)
	}
	if _, err := os.Stat(filepath.Join(parent, "outside")); err == nil {
		t.Error("key escaped the blob root")
	}
	if _, err := os.Stat(filepath.Join(root, "outside")); err != nil {
		t.Errorf("traversal key not confined to root: %v", err)
	}
	if err := client.Put(context.Background(), "", strings.NewReader("x"), 1, ""); err == nil {
		t.Error("empty key accepted")
	}
}

// age sets a stored file's last use to the given time.
func age(t *testing.T, root, key string, lastUsed time.Time) {
	t.Helper()
	if err := os.Chtimes(filepath.Join(root, filepath.FromSlash(key)), lastUsed, lastUsed); err != nil {
		t.Fatalf("chtimes: %v", err)
	}
}

func TestListSkipsTemporaryFiles(t *testing.T) {
	ctx := context.Background()
	root := t.TempDir()
	client, _ := New(root, "http://example.test")
	_ = client.Put(ctx, "cache/a/one.ts", strings.NewReader("12345"), 5, "")
	_ = client.Put(ctx, "cache/two.ts", strings.NewReader("12"), 2, "")
	_ = client.Put(ctx, "other/three.ts", strings.NewReader("1"), 1, "")
	if err := os.WriteFile(filepath.Join(root, "cache", ".put-123"), []byte("partial"), 0o644); err != nil {
		t.Fatal(err)
	}

	files, err := client.List(ctx, "cache/")
	if err != nil {
		t.Fatalf("list: %v", err)
	}
	sizes := map[string]int64{}
	for _, file := range files {
		sizes[file.Key] = file.Size
	}
	if len(sizes) != 2 || sizes["cache/a/one.ts"] != 5 || sizes["cache/two.ts"] != 2 {
		t.Errorf("listed %v", sizes)
	}
	if missing, err := client.List(ctx, "missing/"); err != nil || len(missing) != 0 {
		t.Errorf("missing prefix = %v, %v", missing, err)
	}
}

func TestTouchAndServeBumpLastUse(t *testing.T) {
	ctx := context.Background()
	root := t.TempDir()
	client, _ := New(root, "http://example.test")
	_ = client.Put(ctx, "cache/a.ts", strings.NewReader("x"), 1, "")
	_ = client.Put(ctx, "cache/b.ts", strings.NewReader("x"), 1, "")
	old := time.Now().Add(-time.Hour)
	age(t, root, "cache/a.ts", old)
	age(t, root, "cache/b.ts", old)

	if err := client.Touch(ctx, "cache/a.ts"); err != nil {
		t.Fatalf("touch: %v", err)
	}
	if err := client.Touch(ctx, "cache/missing.ts"); err == nil {
		t.Error("touching a missing key succeeded")
	}
	client.Handler().ServeHTTP(httptest.NewRecorder(), httptest.NewRequest(http.MethodGet, "/api/blob/cache/b.ts", nil))

	files, _ := client.List(ctx, "cache/")
	for _, file := range files {
		if time.Since(file.LastUsed) > time.Minute {
			t.Errorf("%s last used %v, want now", file.Key, file.LastUsed)
		}
	}
}

func TestOnPutReportsWrites(t *testing.T) {
	client, _ := New(t.TempDir(), "http://example.test")
	reported := map[string]int64{}
	client.OnPut(func(key string, size int64) { reported[key] = size })
	_ = client.Put(context.Background(), "cache/a.ts", strings.NewReader("abc"), -1, "")
	if reported["cache/a.ts"] != 3 {
		t.Errorf("reported %v", reported)
	}
}

func TestNewRemovesUnfinishedWrites(t *testing.T) {
	root := t.TempDir()
	directory := filepath.Join(root, "cache", "mp4")
	_ = os.MkdirAll(directory, 0o755)
	for _, name := range []string{".put-123", "kept.mp4"} {
		_ = os.WriteFile(filepath.Join(directory, name), []byte("x"), 0o644)
	}
	if _, err := New(root, "http://example.test"); err != nil {
		t.Fatalf("new: %v", err)
	}
	if _, err := os.Stat(filepath.Join(directory, ".put-123")); err == nil {
		t.Error("unfinished write survived")
	}
	if _, err := os.Stat(filepath.Join(directory, "kept.mp4")); err != nil {
		t.Errorf("stored blob removed: %v", err)
	}
}

func TestKeyFromURL(t *testing.T) {
	cases := map[string]string{
		"http://localhost:8080/api/blob/stream-cache/seg/a.ts": "stream-cache/seg/a.ts",
		"/api/blob/img-cache/b":                                "img-cache/b",
		"https://cdn.example/video.mp4":                        "",
		"http://localhost/api/blob/":                           "",
	}
	for blobURL, want := range cases {
		key, ok := KeyFromURL(blobURL)
		if key != want || ok != (want != "") {
			t.Errorf("KeyFromURL(%q) = %q, %v", blobURL, key, ok)
		}
	}
}
