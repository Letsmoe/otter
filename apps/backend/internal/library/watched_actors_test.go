package library

import (
	"context"
	"testing"

	"github.com/neoworks/otter/internal/db"
)

func TestWatchedActorsRanksActorsInMoreWatchedTitlesFirst(t *testing.T) {
	ctx := context.Background()
	store := newTestStore(t)
	service := &Service{Store: store}

	for _, movie := range []Title{
		{MediaType: MediaTypeMovie, TMDBID: 949, Title: "Heat"},
		{MediaType: MediaTypeMovie, TMDBID: 769, Title: "GoodFellas"},
	} {
		if err := store.SaveMovie(ctx, movie); err != nil {
			t.Fatal(err)
		}
	}
	for _, person := range []Person{
		{TMDBID: 1158, Name: "Al Pacino", ProfilePath: "/pacino.jpg"},
		{TMDBID: 380, Name: "Robert De Niro", ProfilePath: "/deniro.jpg"},
		{TMDBID: 7, Name: "No Portrait"},
		{TMDBID: 8, Name: "Extra", ProfilePath: "/extra.jpg"},
	} {
		if err := store.SavePerson(ctx, person); err != nil {
			t.Fatal(err)
		}
	}
	heat := MediaRef{MediaType: MediaTypeMovie, MediaID: 949}
	goodfellas := MediaRef{MediaType: MediaTypeMovie, MediaID: 769}
	mustCredit(t, store, heat, Credit{PersonID: 1158, Role: "cast", Order: 0})
	mustCredit(t, store, heat, Credit{PersonID: 380, Role: "cast", Order: 1})
	mustCredit(t, store, heat, Credit{PersonID: 7, Role: "cast", Order: 2})
	mustCredit(t, store, heat, Credit{PersonID: 8, Role: "cast", Order: 30})
	mustCredit(t, store, goodfellas, Credit{PersonID: 380, Role: "cast", Order: 0})

	watch := func(ref MediaRef, at string) {
		_, err := store.database.Exec(ctx, `INSERT INTO watch_history (profile_id, media_type, media_id,
			started_at, updated_at) VALUES (1, 'movie', $id, $at, $at)`, db.Vars{"id": ref.MediaID, "at": at})
		if err != nil {
			t.Fatal(err)
		}
	}
	watch(heat, "2026-10-02T00:00:00Z")
	watch(goodfellas, "2026-10-01T00:00:00Z")

	actors, err := service.WatchedActors(ctx, 1, MediaTypeMovie, 10)
	if err != nil {
		t.Fatal(err)
	}
	names := []string{}
	for _, actor := range actors {
		names = append(names, actor.Name)
	}
	if len(names) != 2 || names[0] != "Robert De Niro" || names[1] != "Al Pacino" {
		t.Fatalf("expected De Niro (two titles) then Pacino, without portrait-less or minor cast, got %v", names)
	}

	series, err := service.WatchedActors(ctx, 1, MediaTypeSeries, 10)
	if err != nil {
		t.Fatal(err)
	}
	if len(series) != 0 {
		t.Fatalf("expected no series actors, got %+v", series)
	}
}
