package downloads

import (
	"context"
	"os"
	"path/filepath"
	"strings"
	"testing"

	"github.com/neoworks/otter/internal/db"
	"github.com/neoworks/otter/internal/library"
	"github.com/neoworks/otter/internal/storage"
)

type exportFixture struct {
	exporter *Exporter
	blobs    *storage.Client
	library  *library.Store
}

func newExportFixture(t *testing.T) exportFixture {
	t.Helper()
	database, err := db.Open(context.Background(), filepath.Join(t.TempDir(), "test.db"))
	if err != nil {
		t.Fatal(err)
	}
	t.Cleanup(func() { database.Close() })
	blobs, err := storage.New(filepath.Join(t.TempDir(), "blobs"), "http://local")
	if err != nil {
		t.Fatal(err)
	}
	libraryStore := library.NewStore(database)
	exporter := NewExporter(filepath.Join(t.TempDir(), "downloads"), blobs, libraryStore)
	return exportFixture{exporter: exporter, blobs: blobs, library: libraryStore}
}

func (f exportFixture) putBlob(t *testing.T, key, content string) string {
	t.Helper()
	if err := f.blobs.Put(context.Background(), key, strings.NewReader(content), int64(len(content)), ""); err != nil {
		t.Fatal(err)
	}
	return f.blobs.DirectURL(key)
}

func readFile(t *testing.T, path string) string {
	t.Helper()
	content, err := os.ReadFile(path)
	if err != nil {
		t.Fatal(err)
	}
	return string(content)
}

func TestExportMovieGetsReadableName(t *testing.T) {
	ctx := context.Background()
	fixture := newExportFixture(t)
	movie := library.Title{MediaType: library.MediaTypeMovie, TMDBID: 603, Title: "The Matrix: Reloaded?", ReleaseDate: "2003-05-15"}
	if err := fixture.library.SaveMovie(ctx, movie); err != nil {
		t.Fatal(err)
	}
	fileURL := fixture.putBlob(t, "stream-cache/mp4/a.mp4", "movie bytes")
	download := Download{ID: 1, MediaType: library.MediaTypeMovie, MediaID: 603, FileURL: fileURL, MimeType: "video/mp4"}

	if err := fixture.exporter.Export(ctx, download); err != nil {
		t.Fatal(err)
	}
	exported := filepath.Join(fixture.exporter.Dir, "The Matrix Reloaded (2003).mp4")
	if readFile(t, exported) != "movie bytes" {
		t.Fatal("exported movie has the wrong content")
	}

	if err := fixture.exporter.Remove(ctx, download); err != nil {
		t.Fatal(err)
	}
	if _, err := os.Stat(exported); !os.IsNotExist(err) {
		t.Fatalf("exported movie must be removed: %v", err)
	}
}

func TestExportEpisodeJoinsSegments(t *testing.T) {
	ctx := context.Background()
	fixture := newExportFixture(t)
	series := library.Title{MediaType: library.MediaTypeSeries, TMDBID: 1396, Title: "Breaking Bad"}
	if err := fixture.library.SaveSeries(ctx, series); err != nil {
		t.Fatal(err)
	}
	episode := library.Episode{TMDBID: 62085, SeriesID: 1396, SeasonNumber: 1, EpisodeNumber: 2, Name: "Cat's in the Bag..."}
	if err := fixture.library.SaveEpisodes(ctx, 1396, 1, []library.Episode{episode}); err != nil {
		t.Fatal(err)
	}
	first := fixture.putBlob(t, "stream-cache/seg/1.ts", "first ")
	second := fixture.putBlob(t, "stream-cache/seg/2.ts", "second")
	manifest := "#EXTM3U\n#EXTINF:4,\n" + first + "\n#EXTINF:4,\n" + second + "\n#EXT-X-ENDLIST"
	manifestURL := fixture.putBlob(t, "stream-cache/manifest/a.m3u8", manifest)
	download := Download{ID: 1, MediaType: library.MediaTypeEpisode, MediaID: 62085, FileURL: manifestURL, MimeType: "application/x-mpegURL"}

	if err := fixture.exporter.Export(ctx, download); err != nil {
		t.Fatal(err)
	}
	exported := filepath.Join(fixture.exporter.Dir, "Breaking Bad", "Season 01", "Breaking Bad S01E02 - Cat's in the Bag.ts")
	if readFile(t, exported) != "first second" {
		t.Fatal("segments must be joined in order")
	}

	if err := fixture.exporter.Remove(ctx, download); err != nil {
		t.Fatal(err)
	}
	if _, err := os.Stat(filepath.Join(fixture.exporter.Dir, "Breaking Bad")); !os.IsNotExist(err) {
		t.Fatalf("empty series folder must be removed: %v", err)
	}
}

func TestExportRefusesEncryptedSegments(t *testing.T) {
	ctx := context.Background()
	fixture := newExportFixture(t)
	if err := fixture.library.SaveMovie(ctx, library.Title{MediaType: library.MediaTypeMovie, TMDBID: 603, Title: "The Matrix"}); err != nil {
		t.Fatal(err)
	}
	segment := fixture.putBlob(t, "stream-cache/seg/1.ts", "encrypted")
	manifest := "#EXTM3U\n#EXT-X-KEY:METHOD=AES-128,URI=\"https://example.com/key\"\n#EXTINF:4,\n" + segment
	manifestURL := fixture.putBlob(t, "stream-cache/manifest/a.m3u8", manifest)
	download := Download{ID: 1, MediaType: library.MediaTypeMovie, MediaID: 603, FileURL: manifestURL, MimeType: "application/x-mpegURL"}

	if err := fixture.exporter.Export(ctx, download); err != ErrNotExportable {
		t.Fatalf("err = %v, want ErrNotExportable", err)
	}
}
