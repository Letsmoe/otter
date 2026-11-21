package tmdb

import (
	"encoding/json"
	"testing"
)

func TestMovieDetailParsesItsCollection(t *testing.T) {
	const payload = `{
		"id": 603, "title": "The Matrix",
		"belongs_to_collection": {"id": 2344, "name": "The Matrix Collection", "poster_path": "/p.jpg", "backdrop_path": "/b.jpg"}
	}`
	var detail MovieDetail
	if err := json.Unmarshal([]byte(payload), &detail); err != nil {
		t.Fatal(err)
	}
	collection := detail.BelongsToCollection
	if collection == nil || collection.ID != 2344 || collection.BackdropPath != "/b.jpg" {
		t.Fatalf("collection = %+v", collection)
	}
}

func TestMovieOutsideAFranchiseHasNoCollection(t *testing.T) {
	var detail MovieDetail
	if err := json.Unmarshal([]byte(`{"id": 1, "belongs_to_collection": null}`), &detail); err != nil {
		t.Fatal(err)
	}
	if detail.BelongsToCollection != nil {
		t.Fatalf("collection = %+v, want nil", detail.BelongsToCollection)
	}
}
