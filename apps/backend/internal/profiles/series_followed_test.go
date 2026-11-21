package profiles

import (
	"context"
	"sort"
	"testing"
	"time"

	"github.com/Letsmoe/otter/internal/db"
	"github.com/Letsmoe/otter/internal/library"
)

func saveEpisode(t *testing.T, service *Service, episodeID, seriesID int) {
	t.Helper()
	_, err := service.database.Exec(context.Background(), `INSERT INTO episode (tmdb_id, series_id, season_number, episode_number)
		VALUES ($id, $series_id, 1, 1)`, db.Vars{"id": episodeID, "series_id": seriesID})
	if err != nil {
		t.Fatal(err)
	}
}

func TestFollowedSeriesAreTheListedAndRecentlyWatchedOnes(t *testing.T) {
	ctx := context.Background()
	service := newTestService(t)
	saveEpisode(t, service, 501, 10)
	saveEpisode(t, service, 502, 20)
	listed := library.MediaRef{MediaType: library.MediaTypeSeries, MediaID: 30}
	if err := service.AddToWatchlist(ctx, 1, listed); err != nil {
		t.Fatal(err)
	}
	if _, err := service.RecordProgress(ctx, 1, library.MediaRef{MediaType: library.MediaTypeEpisode, MediaID: 501}, 600, 3000); err != nil {
		t.Fatal(err)
	}
	if _, err := service.RecordProgress(ctx, 2, library.MediaRef{MediaType: library.MediaTypeEpisode, MediaID: 502}, 600, 3000); err != nil {
		t.Fatal(err)
	}

	followed, err := service.FollowedSeriesIDs(ctx, 1, time.Now().Add(-time.Hour))
	if err != nil {
		t.Fatal(err)
	}

	sort.Ints(followed)
	if len(followed) != 2 || followed[0] != 10 || followed[1] != 30 {
		t.Fatalf("followed = %v, want [10 30]", followed)
	}
}

func TestSeriesWatchedLongAgoAreNotFollowed(t *testing.T) {
	ctx := context.Background()
	service := newTestService(t)
	saveEpisode(t, service, 501, 10)
	if _, err := service.RecordProgress(ctx, 1, library.MediaRef{MediaType: library.MediaTypeEpisode, MediaID: 501}, 600, 3000); err != nil {
		t.Fatal(err)
	}

	followed, err := service.FollowedSeriesIDs(ctx, 1, time.Now().Add(time.Hour))
	if err != nil {
		t.Fatal(err)
	}

	if len(followed) != 0 {
		t.Fatalf("followed = %v, want none", followed)
	}
}
