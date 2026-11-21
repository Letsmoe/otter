package franchise

import (
	"context"
	"path/filepath"
	"testing"
	"time"

	"github.com/Letsmoe/otter/internal/db"
	"github.com/Letsmoe/otter/internal/hooks"
	"github.com/Letsmoe/otter/internal/library"
	"github.com/Letsmoe/otter/internal/tmdb"
)

const testProfile = 1

func newTestService(t *testing.T) (*Service, *db.DB) {
	t.Helper()
	database, err := db.Open(context.Background(), filepath.Join(t.TempDir(), "test.db"))
	if err != nil {
		t.Fatal(err)
	}
	t.Cleanup(func() { database.Close() })
	client := tmdb.NewClient(func() (string, string) { return "key", "en-US" })
	libraryService := library.NewService(library.NewStore(database), client, nil, nil, hooks.New(t.TempDir(), nil), nil)
	service := NewService(libraryService, database)
	service.now = func() time.Time { return today }
	return service, database
}

// storeTrilogy saves a collection of three films whose list is already fetched.
func storeTrilogy(t *testing.T, service *Service, collectionID, firstFilmID int) {
	t.Helper()
	ctx := context.Background()
	partIDs := []int{firstFilmID, firstFilmID + 1, firstFilmID + 2}
	for index, id := range partIDs {
		stub := library.Title{MediaType: library.MediaTypeMovie, TMDBID: id, Title: "Film", ReleaseDate: "2000-01-0" + string(rune('1'+index))}
		if err := service.library.Store.SaveStub(ctx, stub); err != nil {
			t.Fatal(err)
		}
	}
	collection := library.Collection{TMDBID: collectionID, Name: "Trilogy", PartIDs: partIDs}
	if err := service.library.Store.SaveCollection(ctx, collection); err != nil {
		t.Fatal(err)
	}
}

func exec(t *testing.T, database *db.DB, query string, vars db.Vars) {
	t.Helper()
	if _, err := database.Exec(context.Background(), query, vars); err != nil {
		t.Fatal(err)
	}
}

func TestGuideReadsTheProfilesProgress(t *testing.T) {
	service, database := newTestService(t)
	storeTrilogy(t, service, 10, 100)
	exec(t, database, `INSERT INTO watch_history (profile_id, media_type, media_id, progress_seconds,
		duration_seconds, completed, started_at, updated_at) VALUES ($profile, 'movie', 100, 7000, 7200, 1, $now, $now)`,
		db.Vars{"profile": testProfile, "now": db.Now()})
	exec(t, database, `INSERT INTO watch_history (profile_id, media_type, media_id, progress_seconds,
		duration_seconds, completed, started_at, updated_at) VALUES ($profile, 'movie', 101, 1800, 7200, 0, $now, $now)`,
		db.Vars{"profile": testProfile, "now": db.Now()})
	// Another profile's progress must not count.
	exec(t, database, `INSERT INTO watch_history (profile_id, media_type, media_id, progress_seconds,
		duration_seconds, completed, started_at, updated_at) VALUES (2, 'movie', 102, 7000, 7200, 1, $now, $now)`,
		db.Vars{"now": db.Now()})

	guide, err := service.Guide(context.Background(), 10, testProfile)
	if err != nil {
		t.Fatal(err)
	}
	statuses := []Status{guide.Parts[0].Status, guide.Parts[1].Status, guide.Parts[2].Status}
	want := []Status{StatusWatched, StatusInProgress, StatusNotYet}
	for index := range want {
		if statuses[index] != want[index] {
			t.Fatalf("statuses = %v, want %v", statuses, want)
		}
	}
	if guide.Parts[1].Progress != 0.25 {
		t.Fatalf("progress = %v, want 0.25", guide.Parts[1].Progress)
	}
	if next := guide.Next(); next == nil || next.Title.TMDBID != 101 {
		t.Fatalf("next = %+v, want the film in progress", next)
	}
}

func TestRatedFilmCountsAsSeen(t *testing.T) {
	service, database := newTestService(t)
	storeTrilogy(t, service, 10, 100)
	exec(t, database, `INSERT INTO user_rating (profile_id, media_type, media_id, rating, rated_at)
		VALUES ($profile, 'movie', 100, 8, $now)`, db.Vars{"profile": testProfile, "now": db.Now()})

	guide, err := service.Guide(context.Background(), 10, testProfile)
	if err != nil {
		t.Fatal(err)
	}
	if guide.Parts[0].Status != StatusWatched {
		t.Fatalf("a rated film should count as watched, got %s", guide.Parts[0].Status)
	}
}

func TestFranchisesPutTheOneInProgressFirst(t *testing.T) {
	service, database := newTestService(t)
	storeTrilogy(t, service, 10, 100)
	storeTrilogy(t, service, 20, 200)
	exec(t, database, `INSERT INTO watch_history (profile_id, media_type, media_id, progress_seconds,
		duration_seconds, completed, started_at, updated_at) VALUES ($profile, 'movie', 200, 7000, 7200, 1, $now, $now)`,
		db.Vars{"profile": testProfile, "now": db.Now()})

	guides, err := service.Franchises(context.Background(), testProfile, 12)
	if err != nil {
		t.Fatal(err)
	}
	if len(guides) != 2 || guides[0].Collection.TMDBID != 20 {
		t.Fatalf("franchises = %+v, want the started collection first", guides)
	}
}

func TestFranchisesSkipSingleFilmCollections(t *testing.T) {
	service, _ := newTestService(t)
	ctx := context.Background()
	if err := service.library.Store.SaveCollection(ctx, library.Collection{TMDBID: 30, Name: "Standalone", PartIDs: []int{300}}); err != nil {
		t.Fatal(err)
	}
	guides, err := service.Franchises(ctx, testProfile, 12)
	if err != nil {
		t.Fatal(err)
	}
	if len(guides) != 0 {
		t.Fatalf("franchises = %+v, want none for a one-film collection", guides)
	}
}
