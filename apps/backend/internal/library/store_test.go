package library

import (
	"context"
	"path/filepath"
	"testing"

	"github.com/neoworks/otter/internal/db"
	"github.com/neoworks/otter/internal/plugins"
)

func newTestStore(t *testing.T) *Store {
	t.Helper()
	database, err := db.Open(context.Background(), filepath.Join(t.TempDir(), "test.db"))
	if err != nil {
		t.Fatal(err)
	}
	t.Cleanup(func() { database.Close() })
	return NewStore(database)
}

func TestStubNeverOverwritesFetchedDetail(t *testing.T) {
	ctx := context.Background()
	store := newTestStore(t)
	movie := Title{MediaType: MediaTypeMovie, TMDBID: 603, Title: "The Matrix", Overview: "full detail",
		Genres: []string{"Action"}, Keywords: []string{"simulation"}, Popularity: 10}
	if err := store.SaveMovie(ctx, movie); err != nil {
		t.Fatal(err)
	}
	stub := Title{MediaType: MediaTypeMovie, TMDBID: 603, Title: "Matrix", Overview: "list blurb", Popularity: 99}
	if err := store.SaveStub(ctx, stub); err != nil {
		t.Fatal(err)
	}
	stored, err := store.Title(ctx, MediaRef{MediaType: MediaTypeMovie, MediaID: 603})
	if err != nil {
		t.Fatal(err)
	}
	if stored.Overview != "full detail" || stored.Popularity != 10 || stored.Keywords[0] != "simulation" {
		t.Fatalf("stub overwrote detail: %+v", stored)
	}
	if stored.DetailFetchedAt == "" {
		t.Fatal("detail_fetched_at not set")
	}
}

func TestPluginStreamsReplaceOnlyTheirOwnLinks(t *testing.T) {
	ctx := context.Background()
	store := newTestStore(t)
	ref := MediaRef{MediaType: MediaTypeMovie, MediaID: 603}
	save := func(pluginName string, urls ...string) {
		downloads := []plugins.Download{}
		for _, url := range urls {
			downloads = append(downloads, plugins.Download{Label: pluginName, URL: url})
		}
		if err := store.ReplacePluginStreams(ctx, ref, pluginName, downloads); err != nil {
			t.Fatal(err)
		}
	}
	save("kinoger", "https://voe.sx/a", "https://voe.sx/b")
	save("s-to", "https://vidoza.net/c")
	if err := store.MarkPlayable(ctx, "https://voe.sx/a", true, 1000, "1080p"); err != nil {
		t.Fatal(err)
	}
	save("kinoger", "https://voe.sx/a")

	streams, err := store.Streams(ctx, ref)
	if err != nil {
		t.Fatal(err)
	}
	if len(streams) != 2 {
		t.Fatalf("streams = %+v, want voe a + vidoza c", streams)
	}
	if streams[0].URL != "https://voe.sx/a" || streams[0].Playable == nil || !*streams[0].Playable {
		t.Fatalf("playable link should stay first and keep its verdict: %+v", streams[0])
	}
	if streams[0].Quality != "1080p" {
		t.Fatalf("a new lookup without a quality must keep the measured one: %+v", streams[0])
	}
}

func TestNextEpisodeCrossesIntoTheNextSeason(t *testing.T) {
	ctx := context.Background()
	store := newTestStore(t)
	episodes := []Episode{
		{TMDBID: 11, SeriesID: 1, SeasonNumber: 1, EpisodeNumber: 1},
		{TMDBID: 12, SeriesID: 1, SeasonNumber: 1, EpisodeNumber: 2},
	}
	if err := store.SaveEpisodes(ctx, 1, 1, episodes); err != nil {
		t.Fatal(err)
	}
	if err := store.SaveEpisodes(ctx, 1, 2, []Episode{{TMDBID: 21, SeriesID: 1, SeasonNumber: 2, EpisodeNumber: 1}}); err != nil {
		t.Fatal(err)
	}
	next, err := store.NextEpisode(ctx, episodes[1])
	if err != nil {
		t.Fatal(err)
	}
	if next == nil || next.TMDBID != 21 {
		t.Fatalf("next = %+v, want S02E01", next)
	}
}

func TestTitlesKeepRequestedOrder(t *testing.T) {
	ctx := context.Background()
	store := newTestStore(t)
	for _, stub := range []Title{
		{MediaType: MediaTypeMovie, TMDBID: 1, Title: "One"},
		{MediaType: MediaTypeSeries, TMDBID: 2, Title: "Two"},
	} {
		if err := store.SaveStub(ctx, stub); err != nil {
			t.Fatal(err)
		}
	}
	titles, err := store.Titles(ctx, []MediaRef{
		{MediaType: MediaTypeSeries, MediaID: 2},
		{MediaType: MediaTypeMovie, MediaID: 404},
		{MediaType: MediaTypeMovie, MediaID: 1},
	})
	if err != nil {
		t.Fatal(err)
	}
	if len(titles) != 2 || titles[0].Title != "Two" || titles[1].Title != "One" {
		t.Fatalf("titles = %+v", titles)
	}
}

func TestAddStreamStoresSharedLinkOnce(t *testing.T) {
	store := newTestStore(t)
	ctx := context.Background()
	ref := MediaRef{MediaType: MediaTypeMovie, MediaID: 603}
	download := plugins.Download{URL: "https://example.com/watch/1", Label: "Shared link"}
	first, err := store.AddStream(ctx, ref, "vidsrc", download)
	if err != nil || first == nil {
		t.Fatalf("AddStream = %v, %v", first, err)
	}
	second, err := store.AddStream(ctx, ref, "vidsrc", download)
	if err != nil || second.ID != first.ID || second.URL != download.URL {
		t.Fatalf("second AddStream = %+v, %v", second, err)
	}
}
