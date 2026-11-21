package recommend

import (
	"testing"

	"github.com/Letsmoe/otter/internal/db"
)

func dismiss(t *testing.T, database *db.DB, movieID int, dismissedAt string) {
	t.Helper()
	exec(t, database, `INSERT INTO dismissed_title (profile_id, media_type, media_id, dismissed_at)
		VALUES ($profile, 'movie', $id, $at)`, db.Vars{"profile": testProfile, "id": movieID, "at": dismissedAt})
}

func TestDismissedTitleNeverComesBack(t *testing.T) {
	engine, database := newTestEngine(t,
		testMovie{id: 1, title: "Hidden", popularity: 999},
		testMovie{id: 2, title: "Fresh", popularity: 10},
	)
	// Years outside the signal window: still hidden.
	dismiss(t, database, 1, "2018-01-01T12:00:00.000Z")

	ids := feedIDs(t, engine)
	if position(ids, 1) >= 0 {
		t.Fatalf("feed = %v, a dismissed movie must not appear", ids)
	}
	if position(ids, 2) < 0 {
		t.Fatalf("feed = %v, the other movie should still be recommended", ids)
	}
}

func TestDismissedTitleOnlyAffectsItsProfile(t *testing.T) {
	engine, database := newTestEngine(t, testMovie{id: 1, title: "Hidden", popularity: 999})
	exec(t, database, `INSERT INTO dismissed_title (profile_id, media_type, media_id, dismissed_at)
		VALUES (2, 'movie', 1, '2026-09-30T12:00:00.000Z')`, nil)

	if ids := feedIDs(t, engine); position(ids, 1) < 0 {
		t.Fatalf("feed = %v, another profile's dismissal must not hide the movie", ids)
	}
}

func TestTitlesLikeADismissedOneRankLower(t *testing.T) {
	movies := []testMovie{
		{id: 1, title: "Hidden Horror", genres: []string{"Horror"}, popularity: 5, director: 77},
		{id: 2, title: "Similar Horror", genres: []string{"Horror"}, popularity: 60, voteAverage: 7, voteCount: 800, director: 77},
		{id: 3, title: "Comedy", genres: []string{"Comedy"}, popularity: 50, voteAverage: 7, voteCount: 800, director: 88},
	}
	engine, _ := newTestEngine(t, movies...)
	if ids := feedIDs(t, engine); position(ids, 2) > position(ids, 3) {
		t.Fatalf("feed = %v, without a dismissal the more popular movie should lead", ids)
	}

	engine, database := newTestEngine(t, movies...)
	dismiss(t, database, 1, "2026-09-30T12:00:00.000Z")
	if ids := feedIDs(t, engine); position(ids, 2) < position(ids, 3) {
		t.Fatalf("feed = %v, the movie sharing genre and director with the dismissed one should rank lower", ids)
	}
}

func TestRelatedTitlesOfADismissedOneRankLower(t *testing.T) {
	movies := []testMovie{
		{id: 1, title: "Hidden", popularity: 5},
		{id: 2, title: "Listed Related", popularity: 60, voteAverage: 7, voteCount: 800},
		{id: 3, title: "Unrelated", popularity: 50, voteAverage: 7, voteCount: 800},
	}
	engine, database := newTestEngine(t, movies...)
	relate(t, database, 1, 2, 0)
	dismiss(t, database, 1, "2026-09-30T12:00:00.000Z")

	if ids := feedIDs(t, engine); position(ids, 2) < position(ids, 3) {
		t.Fatalf("feed = %v, a title related to the dismissed one should rank lower", ids)
	}
}
