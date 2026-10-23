package library

import (
	"context"
	"testing"

	"github.com/neoworks/otter/internal/db"
)

func TestSortByReleasePutsUpcomingFirst(t *testing.T) {
	titles := []Title{
		{Title: "old", ReleaseDate: "2026-01-01"},
		{Title: "later", ReleaseDate: "2026-12-01"},
		{Title: "recent", ReleaseDate: "2026-09-20"},
		{Title: "soon", ReleaseDate: "2026-10-10"},
	}
	sortByRelease(titles, "2026-10-03")
	want := []string{"soon", "later", "recent", "old"}
	for index, title := range titles {
		if title.Title != want[index] {
			t.Fatalf("position %d: got %s, want %s", index, title.Title, want[index])
		}
	}
}

func TestMoreWithLeadUsesLatestWellRatedTitle(t *testing.T) {
	ctx := context.Background()
	store := newTestStore(t)
	service := &Service{Store: store}

	for _, movie := range []Title{
		{MediaType: MediaTypeMovie, TMDBID: 949, Title: "Heat"},
		{MediaType: MediaTypeMovie, TMDBID: 769, Title: "GoodFellas"},
		{MediaType: MediaTypeMovie, TMDBID: 1, Title: "Disliked"},
	} {
		if err := store.SaveMovie(ctx, movie); err != nil {
			t.Fatal(err)
		}
	}
	for _, person := range []Person{{TMDBID: 1158, Name: "Al Pacino"}, {TMDBID: 380, Name: "Robert De Niro"}} {
		if err := store.SavePerson(ctx, person); err != nil {
			t.Fatal(err)
		}
	}
	heat := MediaRef{MediaType: MediaTypeMovie, MediaID: 949}
	goodfellas := MediaRef{MediaType: MediaTypeMovie, MediaID: 769}
	disliked := MediaRef{MediaType: MediaTypeMovie, MediaID: 1}
	mustCredit(t, store, heat, Credit{PersonID: 1158, Role: "cast", Order: 0})
	mustCredit(t, store, heat, Credit{PersonID: 380, Role: "cast", Order: 1})
	mustCredit(t, store, goodfellas, Credit{PersonID: 380, Role: "cast", Order: 0})
	mustCredit(t, store, disliked, Credit{PersonID: 1158, Role: "cast", Order: 0})

	rate := func(ref MediaRef, rating float64, at string) {
		_, err := store.database.Exec(ctx, `INSERT INTO user_rating (profile_id, media_type, media_id, rating, rated_at)
			VALUES (1, 'movie', $id, $rating, $at)`, db.Vars{"id": ref.MediaID, "rating": rating, "at": at})
		if err != nil {
			t.Fatal(err)
		}
	}
	rate(heat, 10, "2026-10-01T00:00:00Z")
	rate(disliked, 2, "2026-10-02T00:00:00Z")

	row, err := service.MoreWithLead(ctx, 1, MediaTypeMovie)
	if err != nil {
		t.Fatal(err)
	}
	if row == nil || row.Person.Name != "Al Pacino" || row.BecauseOf.Title != "Heat" {
		t.Fatalf("expected Al Pacino because of Heat, got %+v", row)
	}
	if len(row.Titles) != 1 || row.Titles[0].Title != "Disliked" {
		t.Fatalf("expected his other movie only, got %+v", row.Titles)
	}
}

func mustCredit(t *testing.T, store *Store, ref MediaRef, credit Credit) {
	t.Helper()
	if err := store.AddCredit(context.Background(), ref, credit); err != nil {
		t.Fatal(err)
	}
}

func TestFilmographyListsEachTitleOnceAndFiltersByJob(t *testing.T) {
	ctx := context.Background()
	store := newTestStore(t)
	for _, movie := range []Title{
		{MediaType: MediaTypeMovie, TMDBID: 1, Title: "Directed", ReleaseDate: "2020-01-01"},
		{MediaType: MediaTypeMovie, TMDBID: 2, Title: "Acted", ReleaseDate: "2010-01-01"},
	} {
		if err := store.SaveMovie(ctx, movie); err != nil {
			t.Fatal(err)
		}
	}
	directed := MediaRef{MediaType: MediaTypeMovie, MediaID: 1}
	acted := MediaRef{MediaType: MediaTypeMovie, MediaID: 2}
	mustCredit(t, store, directed, Credit{PersonID: 7, Role: "crew", Job: "Director"})
	mustCredit(t, store, directed, Credit{PersonID: 7, Role: "crew", Job: "Writer"})
	mustCredit(t, store, directed, Credit{PersonID: 7, Role: "crew", Job: "Producer"})
	mustCredit(t, store, acted, Credit{PersonID: 7, Role: "cast"})

	all, err := store.Filmography(ctx, 7, "")
	if err != nil {
		t.Fatal(err)
	}
	if len(all) != 2 || all[0].Title != "Directed" {
		t.Fatalf("expected each title once, newest first, got %+v", all)
	}
	directing, err := store.Filmography(ctx, 7, "Director")
	if err != nil {
		t.Fatal(err)
	}
	if len(directing) != 1 || directing[0].Title != "Directed" {
		t.Fatalf("expected only the directed title, got %+v", directing)
	}
}
