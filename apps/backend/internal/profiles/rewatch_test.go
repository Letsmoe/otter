package profiles

import (
	"context"
	"testing"

	"github.com/Letsmoe/otter/internal/db"
	"github.com/Letsmoe/otter/internal/library"
)

var (
	testMovie  = library.MediaRef{MediaType: library.MediaTypeMovie, MediaID: 603}
	testSeries = library.MediaRef{MediaType: library.MediaTypeSeries, MediaID: 1396}
)

func addTestEpisodes(t *testing.T, service *Service, seriesID int, episodeIDs ...int) {
	t.Helper()
	for index, episodeID := range episodeIDs {
		_, err := service.database.Exec(context.Background(), `INSERT INTO episode
			(tmdb_id, series_id, season_number, episode_number, air_date)
			VALUES ($id, $series_id, 1, $number, '2015-01-01')`,
			db.Vars{"id": episodeID, "series_id": seriesID, "number": index + 1})
		if err != nil {
			t.Fatal(err)
		}
	}
}

func newProfileWithFinishedMovie(t *testing.T, service *Service) int {
	t.Helper()
	ctx := context.Background()
	profile, err := service.Create(ctx, "Rewatcher", "", "", false)
	if err != nil {
		t.Fatal(err)
	}
	if _, err := service.RecordProgress(ctx, profile.ID, testMovie, 7000, 7200); err != nil {
		t.Fatal(err)
	}
	return profile.ID
}

func TestRewatchOfAMovieKeepsTheFirstWatch(t *testing.T) {
	ctx := context.Background()
	service := newTestService(t)
	profileID := newProfileWithFinishedMovie(t, service)

	if _, err := service.StartRewatch(ctx, profileID, testMovie); err != nil {
		t.Fatal(err)
	}
	fresh, err := service.WatchEntry(ctx, profileID, testMovie)
	if err != nil {
		t.Fatal(err)
	}
	if fresh != nil {
		t.Fatalf("a new rewatch starts without progress, got %+v", fresh)
	}

	entry, err := service.RecordProgress(ctx, profileID, testMovie, 600, 7200)
	if err != nil {
		t.Fatal(err)
	}
	if !entry.Rewatch || entry.ProgressSeconds != 600 || entry.Completed {
		t.Fatalf("unexpected rewatch entry %+v", entry)
	}
	original, err := service.historyEntry(ctx, profileID, testMovie)
	if err != nil {
		t.Fatal(err)
	}
	if original.ProgressSeconds != 7000 || !original.Completed {
		t.Fatalf("the first watch changed: %+v", original)
	}
}

func TestRewatchStaysOutOfHistoryAndContinueWatching(t *testing.T) {
	ctx := context.Background()
	service := newTestService(t)
	profileID := newProfileWithFinishedMovie(t, service)
	otherMovie := library.MediaRef{MediaType: library.MediaTypeMovie, MediaID: 11}

	if _, err := service.StartRewatch(ctx, profileID, testMovie); err != nil {
		t.Fatal(err)
	}
	if _, err := service.RecordProgress(ctx, profileID, testMovie, 600, 7200); err != nil {
		t.Fatal(err)
	}
	if _, err := service.RecordProgress(ctx, profileID, otherMovie, 100, 5000); err != nil {
		t.Fatal(err)
	}
	history, err := service.History(ctx, profileID, 10)
	if err != nil {
		t.Fatal(err)
	}
	for _, entry := range history {
		if entry.MediaID == testMovie.MediaID && entry.ProgressSeconds != 7000 {
			t.Fatalf("history shows rewatch progress: %+v", entry)
		}
	}
	other, err := service.WatchEntry(ctx, profileID, otherMovie)
	if err != nil {
		t.Fatal(err)
	}
	if other == nil || other.Rewatch {
		t.Fatalf("a title that is not rewatched keeps its normal progress, got %+v", other)
	}
}

func TestRewatchOfAMovieFinishesWhenItEnds(t *testing.T) {
	ctx := context.Background()
	service := newTestService(t)
	profileID := newProfileWithFinishedMovie(t, service)
	if _, err := service.StartRewatch(ctx, profileID, testMovie); err != nil {
		t.Fatal(err)
	}

	if _, err := service.RecordProgress(ctx, profileID, testMovie, 7100, 7200); err != nil {
		t.Fatal(err)
	}
	run, err := service.Rewatch(ctx, profileID, testMovie)
	if err != nil {
		t.Fatal(err)
	}
	if run == nil || run.Active() {
		t.Fatalf("the run should be finished: %+v", run)
	}
	entry, err := service.WatchEntry(ctx, profileID, testMovie)
	if err != nil {
		t.Fatal(err)
	}
	if entry.Rewatch {
		t.Error("after the rewatch the first watch is read again")
	}
}

func TestRewatchOfASeriesRunsThroughEveryEpisode(t *testing.T) {
	ctx := context.Background()
	service := newTestService(t)
	profile, err := service.Create(ctx, "Binger", "", "", false)
	if err != nil {
		t.Fatal(err)
	}
	addTestEpisodes(t, service, testSeries.MediaID, 101, 102)
	for _, episodeID := range []int{101, 102} {
		if _, err := service.RecordProgress(ctx, profile.ID, episodeRef(episodeID), 2800, 3000); err != nil {
			t.Fatal(err)
		}
	}
	if _, err := service.StartRewatch(ctx, profile.ID, testSeries); err != nil {
		t.Fatal(err)
	}

	if _, err := service.RecordProgress(ctx, profile.ID, episodeRef(101), 2900, 3000); err != nil {
		t.Fatal(err)
	}
	run, _ := service.Rewatch(ctx, profile.ID, testSeries)
	if !run.Active() {
		t.Fatal("one episode of two must not finish the run")
	}
	latest, err := service.LatestRewatchEpisode(ctx, profile.ID, testSeries.MediaID)
	if err != nil || latest == nil || latest.MediaID != 101 {
		t.Fatalf("latest rewatch episode = %+v, %v", latest, err)
	}
	progress, err := service.EpisodeProgress(ctx, profile.ID, testSeries.MediaID)
	if err != nil || len(progress) != 1 || !progress[0].Rewatch {
		t.Fatalf("during the rewatch episode progress is the rewatch's: %+v, %v", progress, err)
	}

	if _, err := service.RecordProgress(ctx, profile.ID, episodeRef(102), 2900, 3000); err != nil {
		t.Fatal(err)
	}
	run, _ = service.Rewatch(ctx, profile.ID, testSeries)
	if run.Active() {
		t.Fatal("every episode watched again must finish the run")
	}
	progress, _ = service.EpisodeProgress(ctx, profile.ID, testSeries.MediaID)
	if len(progress) != 2 || progress[0].Rewatch {
		t.Fatalf("after the run the first watch's progress is back: %+v", progress)
	}
}

func TestStartingARewatchAgainDropsEarlierProgress(t *testing.T) {
	ctx := context.Background()
	service := newTestService(t)
	profileID := newProfileWithFinishedMovie(t, service)
	if _, err := service.StartRewatch(ctx, profileID, testMovie); err != nil {
		t.Fatal(err)
	}
	if _, err := service.RecordProgress(ctx, profileID, testMovie, 600, 7200); err != nil {
		t.Fatal(err)
	}

	if _, err := service.StartRewatch(ctx, profileID, testMovie); err != nil {
		t.Fatal(err)
	}
	entry, err := service.WatchEntry(ctx, profileID, testMovie)
	if err != nil {
		t.Fatal(err)
	}
	if entry != nil {
		t.Fatalf("a restarted rewatch begins empty, got %+v", entry)
	}
}

func TestStoppingARewatchGoesBackToTheFirstWatch(t *testing.T) {
	ctx := context.Background()
	service := newTestService(t)
	profileID := newProfileWithFinishedMovie(t, service)
	if _, err := service.StartRewatch(ctx, profileID, testMovie); err != nil {
		t.Fatal(err)
	}
	if _, err := service.RecordProgress(ctx, profileID, testMovie, 600, 7200); err != nil {
		t.Fatal(err)
	}

	if err := service.StopRewatch(ctx, profileID, testMovie); err != nil {
		t.Fatal(err)
	}
	run, err := service.Rewatch(ctx, profileID, testMovie)
	if err != nil || run != nil {
		t.Fatalf("the run should be gone: %+v, %v", run, err)
	}
	entry, err := service.WatchEntry(ctx, profileID, testMovie)
	if err != nil {
		t.Fatal(err)
	}
	if entry.Rewatch || entry.ProgressSeconds != 7000 {
		t.Fatalf("the first watch should be back: %+v", entry)
	}
}

func TestOnlyMoviesAndSeriesCanBeRewatched(t *testing.T) {
	ctx := context.Background()
	service := newTestService(t)
	profileID := newProfileWithFinishedMovie(t, service)
	if _, err := service.StartRewatch(ctx, profileID, episodeRef(5)); err != ErrNotRewatchable {
		t.Fatalf("err = %v, want ErrNotRewatchable", err)
	}
}

func TestDeletingAProfileDropsItsRewatches(t *testing.T) {
	ctx := context.Background()
	service := newTestService(t)
	profileID := newProfileWithFinishedMovie(t, service)
	if _, err := service.StartRewatch(ctx, profileID, testMovie); err != nil {
		t.Fatal(err)
	}
	if err := service.Delete(ctx, profileID); err != nil {
		t.Fatal(err)
	}
	count, err := service.database.Int(ctx, "SELECT COUNT(*) FROM rewatch", nil)
	if err != nil || count != 0 {
		t.Fatalf("rewatch rows left: %d, %v", count, err)
	}
}
