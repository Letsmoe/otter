package library

import (
	"context"
	"testing"
)

func TestFilterTitlesByRatingRuntimeAndSeasons(t *testing.T) {
	ctx := context.Background()
	store := newTestStore(t)
	service := &Service{Store: store}

	short := Title{MediaType: MediaTypeMovie, TMDBID: 1, Title: "Short", Runtime: 80, VoteAverage: 8}
	long := Title{MediaType: MediaTypeMovie, TMDBID: 2, Title: "Long", Runtime: 150, VoteAverage: 8}
	weak := Title{MediaType: MediaTypeMovie, TMDBID: 3, Title: "Weak", Runtime: 100, VoteAverage: 5}
	for _, movie := range []Title{short, long, weak} {
		if err := store.SaveMovie(ctx, movie); err != nil {
			t.Fatal(err)
		}
	}
	movies, err := store.Titles(ctx, []MediaRef{short.Ref(), long.Ref(), weak.Ref()})
	if err != nil {
		t.Fatal(err)
	}

	found, err := service.FilterTitles(ctx, movies, TitleFilter{MinRating: 7, MaxRuntime: 120})
	if err != nil {
		t.Fatal(err)
	}
	if len(found) != 1 || found[0].Title != "Short" {
		t.Fatalf("expected only Short, got %+v", found)
	}

	series := Title{MediaType: MediaTypeSeries, TMDBID: 10, Title: "Show", VoteAverage: 8}
	if err := store.SaveSeries(ctx, series); err != nil {
		t.Fatal(err)
	}
	if err := store.SaveSeasons(ctx, []Season{
		{TMDBID: 100, SeriesID: 10, SeasonNumber: 0},
		{TMDBID: 101, SeriesID: 10, SeasonNumber: 1},
		{TMDBID: 102, SeriesID: 10, SeasonNumber: 2},
	}); err != nil {
		t.Fatal(err)
	}
	shows, err := store.Titles(ctx, []MediaRef{series.Ref()})
	if err != nil {
		t.Fatal(err)
	}
	twoOrMore, _ := service.FilterTitles(ctx, shows, TitleFilter{MinSeasons: 2, MaxSeasons: 3})
	fourOrMore, _ := service.FilterTitles(ctx, shows, TitleFilter{MinSeasons: 4})
	if len(twoOrMore) != 1 || len(fourOrMore) != 0 {
		t.Fatalf("season filter: 2-3 got %d, 4+ got %d (specials must not count)", len(twoOrMore), len(fourOrMore))
	}
}
