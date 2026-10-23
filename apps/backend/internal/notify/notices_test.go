package notify

import (
	"context"
	"path/filepath"
	"testing"
	"time"

	"github.com/neoworks/otter/internal/db"
	"github.com/neoworks/otter/internal/library"
)

var today = time.Date(2026, 10, 3, 0, 0, 0, 0, time.UTC)

func movie(releaseDate string) library.Title {
	return library.Title{MediaType: library.MediaTypeMovie, TMDBID: 603, Title: "The Matrix", ReleaseDate: releaseDate}
}

func TestMovieComingSoon(t *testing.T) {
	notices := MovieNotices(movie("2026-10-20"), false, 0, today)
	if len(notices) != 1 || notices[0].Kind != KindComingSoon || notices[0].Heading != "Coming 20 October" {
		t.Fatalf("unexpected notices: %+v", notices)
	}
}

func TestMovieAvailableOnlyWhenWatcherFoundFirstLinks(t *testing.T) {
	if notices := MovieNotices(movie("2020-01-01"), true, 5, today); len(notices) != 0 {
		t.Fatalf("already had links, got %+v", notices)
	}
	if notices := MovieNotices(movie("2020-01-01"), false, 0, today); len(notices) != 0 {
		t.Fatalf("nothing found, got %+v", notices)
	}
	notices := MovieNotices(movie("2020-01-01"), false, 3, today)
	if len(notices) != 1 || notices[0].Kind != KindAvailable || notices[0].Body != "3 sources found" {
		t.Fatalf("unexpected notices: %+v", notices)
	}
}

func TestSeriesNewAndUpcomingEpisodes(t *testing.T) {
	series := library.Title{MediaType: library.MediaTypeSeries, TMDBID: 1}
	episodes := []library.Episode{
		{TMDBID: 10, SeasonNumber: 2, EpisodeNumber: 1, AirDate: "2026-09-01"},
		{TMDBID: 11, SeasonNumber: 2, EpisodeNumber: 2, Name: "Two", AirDate: "2026-09-30"},
		{TMDBID: 12, SeasonNumber: 2, EpisodeNumber: 3, AirDate: "2026-10-07"},
		{TMDBID: 13, SeasonNumber: 2, EpisodeNumber: 4, AirDate: "2026-10-14"},
	}
	notices := SeriesNotices(series, episodes, today)
	if len(notices) != 2 {
		t.Fatalf("want new + upcoming, got %+v", notices)
	}
	if notices[0].Kind != KindNewEpisode || notices[0].EpisodeID != 11 || notices[0].Body != "S2:E2 · Two" {
		t.Fatalf("unexpected new episode notice: %+v", notices[0])
	}
	if notices[1].Kind != KindComingSoon || notices[1].EpisodeID != 12 {
		t.Fatalf("unexpected coming soon notice: %+v", notices[1])
	}
}

func TestNewEpisodeSkipsProfilesThatSavedLater(t *testing.T) {
	notice := Notice{Kind: KindNewEpisode, Date: "2026-09-30"}
	if notice.AppliesTo("2026-10-01T10:00:00.000Z") {
		t.Fatal("saved after airing should not be told")
	}
	if !notice.AppliesTo("2026-09-01T10:00:00.000Z") {
		t.Fatal("saved before airing should be told")
	}
}

func TestStoreDeduplicatesAndMarksRead(t *testing.T) {
	ctx := context.Background()
	database, err := db.Open(ctx, filepath.Join(t.TempDir(), "test.db"))
	if err != nil {
		t.Fatal(err)
	}
	defer database.Close()
	store := NewStore(database)
	notification := Notification{ProfileID: 1, Kind: KindAvailable, MediaType: library.MediaTypeMovie, MediaID: 603, Heading: "Now available"}

	first, err := store.Add(ctx, notification)
	if err != nil || !first {
		t.Fatalf("first add: %v %v", first, err)
	}
	second, err := store.Add(ctx, notification)
	if err != nil || second {
		t.Fatalf("duplicate add should be ignored: %v %v", second, err)
	}
	if count, _ := store.UnreadCount(ctx, 1); count != 1 {
		t.Fatalf("unread = %d, want 1", count)
	}
	if err := store.MarkAllRead(ctx, 1); err != nil {
		t.Fatal(err)
	}
	if count, _ := store.UnreadCount(ctx, 1); count != 0 {
		t.Fatalf("unread after mark = %d, want 0", count)
	}
}
