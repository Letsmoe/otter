package library

import (
	"context"
	"testing"
)

func TestCoStarsCountSharedLeadingRoles(t *testing.T) {
	ctx := context.Background()
	store := newTestStore(t)
	service := &Service{Store: store}

	for _, movie := range []Title{
		{MediaType: MediaTypeMovie, TMDBID: 949, Title: "Heat", Popularity: 50},
		{MediaType: MediaTypeMovie, TMDBID: 1, Title: "The Irishman", Popularity: 40},
		{MediaType: MediaTypeMovie, TMDBID: 2, Title: "Righteous Kill", Popularity: 10},
	} {
		if err := store.SaveMovie(ctx, movie); err != nil {
			t.Fatal(err)
		}
	}
	for _, person := range []Person{
		{TMDBID: 1158, Name: "Al Pacino", ProfilePath: "/pacino.jpg"},
		{TMDBID: 380, Name: "Robert De Niro", ProfilePath: "/deniro.jpg"},
		{TMDBID: 3, Name: "Val Kilmer", ProfilePath: "/kilmer.jpg"},
		{TMDBID: 4, Name: "Extra", ProfilePath: "/extra.jpg"},
	} {
		if err := store.SavePerson(ctx, person); err != nil {
			t.Fatal(err)
		}
	}
	heat := MediaRef{MediaType: MediaTypeMovie, MediaID: 949}
	irishman := MediaRef{MediaType: MediaTypeMovie, MediaID: 1}
	righteousKill := MediaRef{MediaType: MediaTypeMovie, MediaID: 2}
	for _, ref := range []MediaRef{heat, irishman, righteousKill} {
		mustCredit(t, store, ref, Credit{PersonID: 1158, Role: "cast", Order: 0})
		mustCredit(t, store, ref, Credit{PersonID: 380, Role: "cast", Order: 1})
	}
	mustCredit(t, store, heat, Credit{PersonID: 3, Role: "cast", Order: 2})
	mustCredit(t, store, heat, Credit{PersonID: 4, Role: "cast", Order: 40})

	coStars, err := service.CoStars(ctx, 1158, 10)
	if err != nil {
		t.Fatal(err)
	}
	if len(coStars) != 2 {
		t.Fatalf("expected De Niro and Kilmer (not the extra billed 40th), got %+v", coStars)
	}
	if coStars[0].Person.Name != "Robert De Niro" || coStars[0].SharedTitles != 3 {
		t.Fatalf("expected De Niro with 3 shared titles first, got %+v", coStars[0])
	}
	if coStars[1].Person.Name != "Val Kilmer" || coStars[1].SharedTitles != 1 {
		t.Fatalf("expected Kilmer with 1 shared title second, got %+v", coStars[1])
	}
}
