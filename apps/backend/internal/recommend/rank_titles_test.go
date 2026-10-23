package recommend

import (
	"context"
	"testing"

	"github.com/neoworks/otter/internal/library"
)

func TestRankTitlesPutsTasteFirstAndDropsDislikedAndSeen(t *testing.T) {
	engine, database := newTestEngine(t,
		testMovie{id: 1, title: "Loved Thriller", genres: []string{"Thriller"}, popularity: 10, voteAverage: 7, voteCount: 500},
		testMovie{id: 2, title: "Hated Comedy", genres: []string{"Comedy"}, popularity: 10, voteAverage: 7, voteCount: 500},
		testMovie{id: 3, title: "Another Thriller", genres: []string{"Thriller"}, popularity: 10, voteAverage: 7, voteCount: 500},
		testMovie{id: 4, title: "Another Comedy", genres: []string{"Comedy"}, popularity: 10, voteAverage: 7, voteCount: 500},
	)
	rate(t, database, 1, 10)
	rate(t, database, 2, 1)

	refs := []library.MediaRef{}
	for _, id := range []int{1, 2, 3, 4} {
		refs = append(refs, library.MediaRef{MediaType: library.MediaTypeMovie, MediaID: id})
	}
	ranked, err := engine.RankTitles(context.Background(), testProfile, refs)
	if err != nil {
		t.Fatal(err)
	}
	ids := []int{}
	for _, ref := range ranked {
		ids = append(ids, ref.MediaID)
	}
	if position(ids, 2) != -1 {
		t.Fatalf("the disliked comedy must not come back, got %v", ids)
	}
	if position(ids, 3) == -1 || position(ids, 4) == -1 || position(ids, 3) > position(ids, 4) {
		t.Fatalf("expected the thriller ahead of the comedy, got %v", ids)
	}
}
