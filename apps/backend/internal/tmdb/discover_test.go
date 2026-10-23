package tmdb

import (
	"context"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"net/url"
	"testing"
)

func TestDiscoverAsksForGenreSortAndPages(t *testing.T) {
	requests := []url.Values{}
	paths := []string{}
	server := httptest.NewServer(http.HandlerFunc(func(writer http.ResponseWriter, request *http.Request) {
		requests = append(requests, request.URL.Query())
		paths = append(paths, request.URL.Path)
		json.NewEncoder(writer).Encode(SearchResponse{Results: []SearchResult{{ID: len(requests)}}, TotalPages: 5})
	}))
	defer server.Close()

	client := NewClient(func() (string, string) { return "key", "en-US" }).WithBaseURL(server.URL)
	results, err := client.Discover(context.Background(), "series", 18, SortTopRated, 2)
	if err != nil {
		t.Fatal(err)
	}
	if len(results) != 2 || len(requests) != 2 {
		t.Fatalf("expected 2 pages, got %d results from %d requests", len(results), len(requests))
	}
	if paths[0] != "/discover/tv" {
		t.Fatalf("series should use /discover/tv, got %s", paths[0])
	}
	first := requests[0]
	if first.Get("with_genres") != "18" || first.Get("sort_by") != SortTopRated || first.Get("page") != "1" {
		t.Fatalf("unexpected query %v", first)
	}
	if requests[1].Get("page") != "2" {
		t.Fatalf("second request should ask for page 2, got %v", requests[1])
	}
}

func TestDiscoverWithoutGenreStopsAtLastPage(t *testing.T) {
	requests := 0
	server := httptest.NewServer(http.HandlerFunc(func(writer http.ResponseWriter, request *http.Request) {
		requests++
		if request.URL.Query().Has("with_genres") {
			t.Errorf("genre 0 should not filter, got %v", request.URL.Query())
		}
		json.NewEncoder(writer).Encode(SearchResponse{Results: []SearchResult{{ID: 1}}, TotalPages: 1})
	}))
	defer server.Close()

	client := NewClient(func() (string, string) { return "key", "en-US" }).WithBaseURL(server.URL)
	if _, err := client.Discover(context.Background(), "movie", 0, SortPopular, 3); err != nil {
		t.Fatal(err)
	}
	if requests != 1 {
		t.Fatalf("expected 1 request when TMDB has one page, got %d", requests)
	}
}

func TestDiscoverWithSendsThemeFilters(t *testing.T) {
	var query url.Values
	server := httptest.NewServer(http.HandlerFunc(func(writer http.ResponseWriter, request *http.Request) {
		query = request.URL.Query()
		json.NewEncoder(writer).Encode(SearchResponse{Results: []SearchResult{{ID: 1}}, TotalPages: 1})
	}))
	defer server.Close()

	client := NewClient(func() (string, string) { return "key", "en-US" }).WithBaseURL(server.URL)
	options := DiscoverOptions{GenreIDs: "35,10751", Sort: SortPopular, MinVotes: 200, MaxVotes: 2000, MinRating: 7.5}
	if _, err := client.DiscoverWith(context.Background(), "movie", options, 1); err != nil {
		t.Fatal(err)
	}
	expected := map[string]string{
		"with_genres": "35,10751", "vote_count.gte": "200", "vote_count.lte": "2000", "vote_average.gte": "7.5",
	}
	for key, value := range expected {
		if query.Get(key) != value {
			t.Fatalf("%s: expected %s, got %q (%v)", key, value, query.Get(key), query)
		}
	}
}

func TestDiscoverWithDefaultsToMinimumVotes(t *testing.T) {
	params := DiscoverOptions{Sort: SortRevenue}.params()
	if params["vote_count.gte"] != minimumVotes || params["sort_by"] != SortRevenue {
		t.Fatalf("unexpected params %v", params)
	}
	if _, found := params["vote_count.lte"]; found {
		t.Fatalf("no upper vote bound expected, got %v", params)
	}
}
