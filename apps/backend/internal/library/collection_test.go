package library

import (
	"context"
	"net/http"
	"net/http/httptest"
	"path/filepath"
	"strings"
	"sync/atomic"
	"testing"

	"github.com/Letsmoe/otter/internal/db"
	"github.com/Letsmoe/otter/internal/hooks"
	"github.com/Letsmoe/otter/internal/tmdb"
)

const matrixCollectionPayload = `{
	"id": 2344, "name": "The Matrix Collection", "overview": "Wake up.",
	"poster_path": "/poster.jpg", "backdrop_path": "/backdrop.jpg",
	"parts": [
		{"id": 624860, "title": "The Matrix Resurrections", "release_date": "2021-12-16"},
		{"id": 604, "title": "The Matrix Reloaded", "release_date": "2003-05-15"},
		{"id": 605, "title": "The Matrix Revolutions", "release_date": "2003-11-05"},
		{"id": 603, "title": "The Matrix", "release_date": "1999-03-30"},
		{"id": 999999, "title": "Untitled Matrix Project", "release_date": ""}
	]
}`

// newCollectionService is a library service against a fake TMDB that serves
// one collection and counts its requests.
func newCollectionService(t *testing.T, requests *atomic.Int32) *Service {
	t.Helper()
	server := httptest.NewServer(http.HandlerFunc(func(writer http.ResponseWriter, request *http.Request) {
		switch {
		case strings.HasPrefix(request.URL.Path, "/collection/2344"):
			requests.Add(1)
			writer.Write([]byte(matrixCollectionPayload))
		case strings.HasPrefix(request.URL.Path, "/genre/"):
			writer.Write([]byte(`{"genres": []}`))
		default:
			http.NotFound(writer, request)
		}
	}))
	t.Cleanup(server.Close)
	database, err := db.Open(context.Background(), filepath.Join(t.TempDir(), "test.db"))
	if err != nil {
		t.Fatal(err)
	}
	t.Cleanup(func() { database.Close() })
	client := tmdb.NewClient(func() (string, string) { return "key", "en-US" }).WithBaseURL(server.URL)
	return NewService(NewStore(database), client, nil, nil, hooks.New(t.TempDir(), nil), nil)
}

func titleIDs(titles []Title) []int {
	ids := make([]int, 0, len(titles))
	for _, title := range titles {
		ids = append(ids, title.TMDBID)
	}
	return ids
}

func TestEnsureCollectionStoresFilmsInReleaseOrder(t *testing.T) {
	var requests atomic.Int32
	service := newCollectionService(t, &requests)

	collection, titles, err := service.EnsureCollection(context.Background(), 2344)
	if err != nil {
		t.Fatal(err)
	}
	if collection.Name != "The Matrix Collection" || collection.BackdropPath != "/backdrop.jpg" {
		t.Fatalf("collection = %+v", collection)
	}
	want := []int{603, 604, 605, 624860, 999999}
	got := titleIDs(titles)
	if len(got) != len(want) {
		t.Fatalf("films = %v, want %v", got, want)
	}
	for index := range want {
		if got[index] != want[index] {
			t.Fatalf("films = %v, want %v (release order, undated last)", got, want)
		}
	}
	if titles[0].CollectionID != 2344 {
		t.Fatalf("a film only seen in the list should know its franchise, got collection %d", titles[0].CollectionID)
	}
}

func TestEnsureCollectionFetchesOnlyWhenStale(t *testing.T) {
	var requests atomic.Int32
	service := newCollectionService(t, &requests)
	for range 3 {
		if _, _, err := service.EnsureCollection(context.Background(), 2344); err != nil {
			t.Fatal(err)
		}
	}
	if requests.Load() != 1 {
		t.Fatalf("TMDB was asked %d times, want once", requests.Load())
	}
}

func TestMovieDetailRemembersItsCollection(t *testing.T) {
	var requests atomic.Int32
	service := newCollectionService(t, &requests)
	ctx := context.Background()
	err := service.saveCollectionSummary(ctx, &tmdb.CollectionSummary{ID: 2344, Name: "The Matrix Collection", PosterPath: "/p.jpg"})
	if err != nil {
		t.Fatal(err)
	}
	summary, err := service.Store.Collection(ctx, 2344)
	if err != nil || summary == nil {
		t.Fatalf("summary = %v, %v", summary, err)
	}
	if summary.DetailFetchedAt != "" || len(summary.PartIDs) != 0 {
		t.Fatalf("a summary has no film list yet: %+v", summary)
	}
	if _, _, err := service.EnsureCollection(ctx, 2344); err != nil {
		t.Fatal(err)
	}
	if requests.Load() != 1 {
		t.Fatalf("a summary alone must not count as a fetched collection, requests = %d", requests.Load())
	}
}

func TestReleaseOrderKeepsTMDBOrderForEqualDates(t *testing.T) {
	parts := []tmdb.SearchResult{
		{ID: 3, ReleaseDate: "2010-01-01"},
		{ID: 1, ReleaseDate: "2010-01-01"},
		{ID: 2, ReleaseDate: "2005-06-01"},
	}
	got := releaseOrder(parts)
	want := []int{2, 3, 1}
	for index := range want {
		if got[index] != want[index] {
			t.Fatalf("order = %v, want %v", got, want)
		}
	}
}
