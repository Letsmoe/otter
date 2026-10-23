package subtitles

import (
	"bytes"
	"compress/gzip"
	"context"
	"net/http"
	"net/http/httptest"
	"path/filepath"
	"strings"
	"sync/atomic"
	"testing"

	"github.com/neoworks/otter/internal/cache"
	"github.com/neoworks/otter/internal/db"
	"github.com/neoworks/otter/internal/plugins"
	"github.com/neoworks/otter/internal/storage"
)

// windows1252Subtitle holds ü as the single byte 0xFC and an ad cue.
const windows1252Subtitle = "1\r\n00:00:01,000 --> 00:00:03,000\r\nGr\xfc\xdfe & <i>hi</i>\r\n\r\n" +
	"2\r\n00:00:05,000 --> 00:00:06,000\r\nGet more at opensubtitles.org\r\n"

type testSetup struct {
	service  *Service
	store    *Store
	requests *atomic.Int32
	source   *httptest.Server
}

func newTestSetup(t *testing.T, body func() []byte) testSetup {
	t.Helper()
	ctx := context.Background()
	database, err := db.Open(ctx, filepath.Join(t.TempDir(), "test.db"))
	if err != nil {
		t.Fatal(err)
	}
	t.Cleanup(func() { database.Close() })
	blobs, err := storage.New(filepath.Join(t.TempDir(), "blobs"), "http://localhost")
	if err != nil {
		t.Fatal(err)
	}
	requests := &atomic.Int32{}
	source := httptest.NewServer(http.HandlerFunc(func(writer http.ResponseWriter, request *http.Request) {
		requests.Add(1)
		if request.Header.Get("X-Test") != "yes" {
			http.Error(writer, "missing header", http.StatusForbidden)
			return
		}
		writer.Write(body())
	}))
	t.Cleanup(source.Close)
	store := NewStore(database)
	service := NewService(store, nil, nil, nil, cache.New(ctx, database), blobs, func() error { return nil })
	return testSetup{service: service, store: store, requests: requests, source: source}
}

func gzippedBytes(content string) []byte {
	var buffer bytes.Buffer
	writer := gzip.NewWriter(&buffer)
	writer.Write([]byte(content))
	writer.Close()
	return buffer.Bytes()
}

func (setup testSetup) storeFile(t *testing.T, language string) string {
	t.Helper()
	file := plugins.SubtitleFile{
		URL: setup.source.URL + "/file.gz", Language: language, Format: "srt", Release: "Rel",
		Headers: map[string]string{"X-Test": "yes"},
	}
	if err := setup.store.ReplacePluginFiles(context.Background(), "movie:1", "test", []plugins.SubtitleFile{file}); err != nil {
		t.Fatal(err)
	}
	return SubtitleID("movie:1", "test", file.URL)
}

func get(t *testing.T, handler http.Handler, target string) *httptest.ResponseRecorder {
	t.Helper()
	recorder := httptest.NewRecorder()
	handler.ServeHTTP(recorder, httptest.NewRequest(http.MethodGet, target, nil))
	return recorder
}

func TestHandlerServesCleanedVTTAndSRT(t *testing.T) {
	setup := newTestSetup(t, func() []byte { return gzippedBytes(windows1252Subtitle) })
	id := setup.storeFile(t, "de")
	handler := setup.service.Handler()

	vtt := get(t, handler, RoutePrefix+id+".vtt")
	if vtt.Code != http.StatusOK || vtt.Header().Get("Content-Type") != "text/vtt; charset=utf-8" {
		t.Fatalf("vtt: %d %q", vtt.Code, vtt.Header().Get("Content-Type"))
	}
	wantVTT := "WEBVTT\n\n00:00:01.000 --> 00:00:03.000\nGrüße &amp; <i>hi</i>\n\n"
	if vtt.Body.String() != wantVTT {
		t.Errorf("vtt body = %q, want %q", vtt.Body.String(), wantVTT)
	}

	srt := get(t, handler, RoutePrefix+id+".srt")
	if srt.Header().Get("Content-Type") != "application/x-subrip; charset=utf-8" {
		t.Errorf("srt content type: %q", srt.Header().Get("Content-Type"))
	}
	if !strings.Contains(srt.Body.String(), "1\n00:00:01,000 --> 00:00:03,000\nGrüße & <i>hi</i>\n") {
		t.Errorf("srt body = %q", srt.Body.String())
	}
	if setup.requests.Load() != 1 {
		t.Errorf("the cleaned file should be cached, got %d downloads", setup.requests.Load())
	}
}

func TestHandlerShiftsCuesByOffset(t *testing.T) {
	setup := newTestSetup(t, func() []byte { return []byte(windows1252Subtitle) })
	id := setup.storeFile(t, "de")
	handler := setup.service.Handler()

	later := get(t, handler, RoutePrefix+id+".vtt?offset=1500")
	if !strings.Contains(later.Body.String(), "00:00:02.500 --> 00:00:04.500") {
		t.Errorf("later: %q", later.Body.String())
	}
	earlier := get(t, handler, RoutePrefix+id+".srt?offset=-500")
	if !strings.Contains(earlier.Body.String(), "00:00:00,500 --> 00:00:02,500") {
		t.Errorf("earlier: %q", earlier.Body.String())
	}
	if bad := get(t, handler, RoutePrefix+id+".vtt?offset=abc"); bad.Code != http.StatusBadRequest {
		t.Errorf("bad offset: %d", bad.Code)
	}
}

func TestHandlerRejectsUnknownAndMalformedIDs(t *testing.T) {
	setup := newTestSetup(t, func() []byte { return nil })
	handler := setup.service.Handler()
	unknown := strings.Repeat("a", 32)
	if recorder := get(t, handler, RoutePrefix+unknown+".vtt"); recorder.Code != http.StatusNotFound {
		t.Errorf("unknown id: %d", recorder.Code)
	}
	for _, name := range []string{"../etc.vtt", unknown + ".txt", "short.vtt", unknown} {
		if recorder := get(t, handler, RoutePrefix+name); recorder.Code != http.StatusNotFound {
			t.Errorf("%s: %d", name, recorder.Code)
		}
	}
}

func TestHandlerReportsBrokenSources(t *testing.T) {
	setup := newTestSetup(t, func() []byte { return []byte("not a subtitle file") })
	id := setup.storeFile(t, "de")
	if recorder := get(t, setup.service.Handler(), RoutePrefix+id+".vtt"); recorder.Code != http.StatusBadGateway {
		t.Errorf("a file without captions should be a bad gateway, got %d", recorder.Code)
	}
}

func TestReplacePluginFilesKeepsOtherPluginsAndDropsGoneFiles(t *testing.T) {
	setup := newTestSetup(t, func() []byte { return nil })
	ctx := context.Background()
	one := plugins.SubtitleFile{URL: "https://x/1", Language: "de", Format: "srt"}
	two := plugins.SubtitleFile{URL: "https://x/2", Language: "en", Format: "srt", HearingImpaired: true, Headers: map[string]string{"A": "b"}}
	other := plugins.SubtitleFile{URL: "https://x/3", Language: "de", Format: "srt"}
	if err := setup.store.ReplacePluginFiles(ctx, "movie:1", "first", []plugins.SubtitleFile{one, two}); err != nil {
		t.Fatal(err)
	}
	if err := setup.store.ReplacePluginFiles(ctx, "movie:1", "second", []plugins.SubtitleFile{other}); err != nil {
		t.Fatal(err)
	}
	if err := setup.store.ReplacePluginFiles(ctx, "movie:1", "first", []plugins.SubtitleFile{two}); err != nil {
		t.Fatal(err)
	}

	stored, err := setup.store.ForMedia(ctx, "movie:1")
	if err != nil {
		t.Fatal(err)
	}
	if len(stored) != 2 {
		t.Fatalf("stored: %+v", stored)
	}
	for _, file := range stored {
		if file.Plugin == "first" && (!file.HearingImpaired || file.Headers["A"] != "b") {
			t.Errorf("first plugin's file lost its fields: %+v", file)
		}
	}
}

func TestUsableFilesFillsFormatAndDropsIncompleteEntries(t *testing.T) {
	files := usableFiles([]plugins.SubtitleFile{
		{URL: "https://x/1", Language: "DE"},
		{URL: "", Language: "de"},
		{URL: "https://x/2"},
	})
	if len(files) != 1 || files[0].Format != "srt" || files[0].Language != "de" {
		t.Errorf("got %+v", files)
	}
}

func TestLookupDueWhenLanguagesChangeOrExpire(t *testing.T) {
	setup := newTestSetup(t, func() []byte { return nil })
	ctx := context.Background()
	if !setup.service.lookupDue(ctx, "movie:1", []string{"de"}) {
		t.Error("a title never looked up is due")
	}
	if err := setup.service.markLookedUp(ctx, "movie:1", []string{"de", "en"}); err != nil {
		t.Fatal(err)
	}
	if setup.service.lookupDue(ctx, "movie:1", []string{"en", "de"}) {
		t.Error("languages already asked for are not due")
	}
	if !setup.service.lookupDue(ctx, "movie:1", []string{"de", "fr"}) {
		t.Error("a new language is due")
	}
}
