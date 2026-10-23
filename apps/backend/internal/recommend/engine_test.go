package recommend

import (
	"context"
	"path/filepath"
	"testing"
	"time"

	"github.com/neoworks/otter/internal/db"
	"github.com/neoworks/otter/internal/library"
)

const testProfile = 1

type testMovie struct {
	id          int
	title       string
	genres      []string
	popularity  float64
	voteAverage float64
	voteCount   int
	director    int
}

func newTestEngine(t *testing.T, movies ...testMovie) (*Engine, *db.DB) {
	t.Helper()
	ctx := context.Background()
	database, err := db.Open(ctx, filepath.Join(t.TempDir(), "test.db"))
	if err != nil {
		t.Fatal(err)
	}
	t.Cleanup(func() { database.Close() })
	for _, movie := range movies {
		insertMovie(t, database, movie)
	}
	engine := New(database)
	engine.now = func() time.Time { return time.Date(2026, 10, 1, 12, 0, 0, 0, time.UTC) }
	return engine, database
}

func insertMovie(t *testing.T, database *db.DB, movie testMovie) {
	t.Helper()
	ctx := context.Background()
	_, err := database.Exec(ctx, `INSERT INTO movie (tmdb_id, title, genres, popularity, vote_average,
		vote_count, release_date, created_at) VALUES ($id, $title, $genres, $popularity, $vote_average,
		$vote_count, '2020-01-01', $now)`, db.Vars{
		"id": movie.id, "title": movie.title, "genres": movie.genres, "popularity": movie.popularity,
		"vote_average": movie.voteAverage, "vote_count": movie.voteCount, "now": db.Now(),
	})
	if err != nil {
		t.Fatal(err)
	}
	if movie.director == 0 {
		return
	}
	_, err = database.Exec(ctx, `INSERT INTO credit (media_type, media_id, person_id, role, job)
		VALUES ('movie', $id, $person, 'crew', 'Director')`, db.Vars{"id": movie.id, "person": movie.director})
	if err != nil {
		t.Fatal(err)
	}
}

func exec(t *testing.T, database *db.DB, query string, vars db.Vars) {
	t.Helper()
	if _, err := database.Exec(context.Background(), query, vars); err != nil {
		t.Fatal(err)
	}
}

func rate(t *testing.T, database *db.DB, movieID int, rating float64) {
	exec(t, database, `INSERT INTO user_rating (profile_id, media_type, media_id, rating, rated_at)
		VALUES ($profile, 'movie', $id, $rating, '2026-09-30T12:00:00.000Z')`,
		db.Vars{"profile": testProfile, "id": movieID, "rating": rating})
}

func relate(t *testing.T, database *db.DB, fromID, toID, rank int) {
	exec(t, database, `INSERT INTO related (media_type, media_id, related_type, related_id, source, rank)
		VALUES ('movie', $from, 'movie', $to, 'tmdb', $rank)`, db.Vars{"from": fromID, "to": toID, "rank": rank})
}

func feedIDs(t *testing.T, engine *Engine) []int {
	t.Helper()
	items, err := engine.Feed(context.Background(), testProfile, "", 50, 0, true)
	if err != nil {
		t.Fatal(err)
	}
	ids := make([]int, 0, len(items))
	for _, item := range items {
		ids = append(ids, item.Media.MediaID)
	}
	return ids
}

func position(ids []int, id int) int {
	for index, candidate := range ids {
		if candidate == id {
			return index
		}
	}
	return -1
}

func TestColdStartFallsBackToPopularity(t *testing.T) {
	engine, _ := newTestEngine(t,
		testMovie{id: 1, title: "Quiet", popularity: 5},
		testMovie{id: 2, title: "Blockbuster", popularity: 500},
	)
	ids := feedIDs(t, engine)
	if len(ids) != 2 || ids[0] != 2 {
		t.Fatalf("cold start feed = %v, want the popular movie first", ids)
	}
}

func TestRelatedTitlesOfALovedMovieRankFirst(t *testing.T) {
	engine, database := newTestEngine(t,
		testMovie{id: 1, title: "Heat", genres: []string{"Crime"}, popularity: 50, voteAverage: 8, voteCount: 5000},
		testMovie{id: 2, title: "Collateral", genres: []string{"Crime"}, popularity: 30, voteAverage: 7.5, voteCount: 4000},
		testMovie{id: 3, title: "Blockbuster", genres: []string{"Family"}, popularity: 900, voteAverage: 6, voteCount: 9000},
	)
	rate(t, database, 1, 10)
	relate(t, database, 1, 2, 0)

	ids := feedIDs(t, engine)
	if ids[0] != 2 {
		t.Fatalf("feed = %v, want the related movie first", ids)
	}
	if position(ids, 1) >= 0 {
		t.Fatalf("feed = %v, the rated (so already seen) movie must not come back", ids)
	}
	rows, err := engine.Rows(context.Background(), testProfile, 8, 20)
	if err != nil {
		t.Fatal(err)
	}
	for _, row := range rows {
		if row.Reason.Kind == ReasonRelated && row.Title != "Because you watched Heat" {
			t.Fatalf("related row title = %q", row.Title)
		}
	}
}

func TestDislikedAndFinishedMoviesAreExcluded(t *testing.T) {
	engine, database := newTestEngine(t,
		testMovie{id: 1, title: "Bad", popularity: 999},
		testMovie{id: 2, title: "Seen", popularity: 998},
		testMovie{id: 3, title: "Fresh", popularity: 10},
	)
	rate(t, database, 1, 2)
	exec(t, database, `INSERT INTO watch_history (profile_id, media_type, media_id, progress_seconds,
		duration_seconds, completed, started_at, updated_at)
		VALUES ($profile, 'movie', 2, 7000, 7200, 1, '2026-09-29T12:00:00.000Z', '2026-09-29T12:00:00.000Z')`,
		db.Vars{"profile": testProfile})

	ids := feedIDs(t, engine)
	if position(ids, 1) >= 0 {
		t.Fatalf("feed = %v, disliked movie must not appear", ids)
	}
	if position(ids, 2) >= 0 || position(ids, 3) < 0 {
		t.Fatalf("feed = %v, want only the unseen movie", ids)
	}
}

func TestDirectorAffinityPullsInTheirOtherWork(t *testing.T) {
	engine, database := newTestEngine(t,
		testMovie{id: 1, title: "Heat", popularity: 50, director: 77},
		testMovie{id: 2, title: "Thief", popularity: 5, voteAverage: 7, voteCount: 800, director: 77},
		testMovie{id: 3, title: "Other", popularity: 40, voteAverage: 7, voteCount: 800, director: 88},
	)
	rate(t, database, 1, 9)
	ids := feedIDs(t, engine)
	if position(ids, 2) > position(ids, 3) {
		t.Fatalf("feed = %v, the loved director's film should rank above an unrelated one", ids)
	}
}

func TestWatchWeightClassifiesWatches(t *testing.T) {
	cases := []struct {
		name      string
		progress  float64
		duration  float64
		age       time.Duration
		weight    float64
		countable bool
	}{
		{"completed", 6500, 7000, time.Hour, weightCompletedWatch, true},
		{"partial", 3000, 7000, time.Hour, weightPartialWatch, true},
		{"abandoned", 60, 7000, time.Hour, weightEarlyAbandon, true},
		{"just started", 60, 7000, time.Minute, 0, false},
	}
	for _, testCase := range cases {
		weight, counts := watchWeight(testCase.progress, testCase.duration, false, testCase.age)
		if weight != testCase.weight || counts != testCase.countable {
			t.Errorf("%s: got (%v, %v)", testCase.name, weight, counts)
		}
	}
}

func TestRerankCapsOneDirectorPerPage(t *testing.T) {
	scored := []scoredItem{}
	for index := range 5 {
		scored = append(scored, scoredItem{
			Item:     Item{Media: library.MediaRef{MediaType: library.MediaTypeMovie, MediaID: index + 1}, Source: SourceDirector},
			features: &titleFeatures{directors: []int{77}},
		})
	}
	scored = append(scored, scoredItem{
		Item:     Item{Media: library.MediaRef{MediaType: library.MediaTypeMovie, MediaID: 99}, Source: SourceGenre},
		features: &titleFeatures{},
	})
	ranked := rerank(scored)
	if ranked[2].Media.MediaID != 99 {
		t.Fatalf("third slot = %d, want the other director's film after two of director 77", ranked[2].Media.MediaID)
	}
}
