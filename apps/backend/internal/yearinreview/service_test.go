package yearinreview

import (
	"context"
	"path/filepath"
	"testing"

	"github.com/Letsmoe/otter/internal/db"
	"github.com/Letsmoe/otter/internal/library"
)

const testProfile = 1

func newTestService(t *testing.T) (*Service, *db.DB, *library.Store) {
	t.Helper()
	database, err := db.Open(context.Background(), filepath.Join(t.TempDir(), "test.db"))
	if err != nil {
		t.Fatal(err)
	}
	t.Cleanup(func() { database.Close() })
	store := library.NewStore(database)
	return NewService(database, store), database, store
}

func recordWatch(t *testing.T, database *db.DB, profileID int, mediaType string, mediaID int, progress float64, completed bool, startedAt, updatedAt string) {
	t.Helper()
	_, err := database.Exec(context.Background(), `INSERT INTO watch_history (profile_id, media_type, media_id,
		progress_seconds, duration_seconds, completed, started_at, updated_at)
		VALUES ($profile, $type, $id, $progress, 2700, $completed, $started, $updated)`,
		db.Vars{"profile": profileID, "type": mediaType, "id": mediaID, "progress": progress,
			"completed": completed, "started": startedAt, "updated": updatedAt})
	if err != nil {
		t.Fatal(err)
	}
}

func TestYearReadsHistoryEpisodesAndGenres(t *testing.T) {
	ctx := context.Background()
	service, database, store := newTestService(t)
	series := library.Title{MediaType: library.MediaTypeSeries, TMDBID: 5, Title: "Breaking Bad", Genres: []string{"Drama"}, ReleaseDate: "2008-01-20"}
	if err := store.SaveSeries(ctx, series); err != nil {
		t.Fatal(err)
	}
	episodes := []library.Episode{
		{TMDBID: 501, SeriesID: 5, SeasonNumber: 1, EpisodeNumber: 1, Name: "Pilot"},
		{TMDBID: 502, SeriesID: 5, SeasonNumber: 1, EpisodeNumber: 2, Name: "Cat's in the Bag"},
	}
	if err := store.SaveEpisodes(ctx, 5, 1, episodes); err != nil {
		t.Fatal(err)
	}
	recordWatch(t, database, testProfile, "episode", 501, 2600, true, "2026-05-01T20:00:00.000Z", "2026-05-01T20:50:00.000Z")
	recordWatch(t, database, testProfile, "episode", 502, 2600, true, "2026-05-01T21:00:00.000Z", "2026-05-01T21:50:00.000Z")
	// Another profile's history stays out.
	recordWatch(t, database, 2, "episode", 501, 2600, true, "2026-05-01T20:00:00.000Z", "2026-05-01T20:50:00.000Z")

	result, err := service.Year(ctx, testProfile, 2026, 0)
	if err != nil {
		t.Fatal(err)
	}
	review := result.Review
	if review.EpisodesFinished != 2 || review.SeriesWatched != 1 || review.WatchedSeconds != 5200 {
		t.Fatalf("review = %+v", review)
	}
	if review.TopSeries == nil || review.TopSeries.SeriesID != 5 || review.LongestBinge == nil || review.LongestBinge.Episodes != 2 {
		t.Fatalf("top series %+v, binge %+v", review.TopSeries, review.LongestBinge)
	}
	if len(review.TopGenres) != 1 || review.TopGenres[0].Name != "Drama" {
		t.Fatalf("genres = %+v", review.TopGenres)
	}
	if title, found := result.Titles[seriesRef(5)]; !found || title.Title != "Breaking Bad" {
		t.Fatalf("titles = %+v", result.Titles)
	}
}

func TestYearWithoutHistoryIsEmpty(t *testing.T) {
	service, _, _ := newTestService(t)
	result, err := service.Year(context.Background(), testProfile, 2026, 0)
	if err != nil {
		t.Fatal(err)
	}
	if result.Review.HasActivity {
		t.Fatalf("review = %+v", result.Review)
	}
}
