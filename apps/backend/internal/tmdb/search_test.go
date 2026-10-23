package tmdb

import (
	"context"
	"encoding/json"
	"fmt"
	"net/http"
	"net/http/httptest"
	"testing"
)

func TestSearchMultiReadsFurtherPagesUpToLimit(t *testing.T) {
	requestedPages := []string{}
	server := httptest.NewServer(http.HandlerFunc(func(writer http.ResponseWriter, request *http.Request) {
		page := request.URL.Query().Get("page")
		requestedPages = append(requestedPages, page)
		results := []SearchResult{}
		for index := 0; index < 20; index++ {
			mediaType := "movie"
			if index%10 == 0 {
				mediaType = "person"
			}
			results = append(results, SearchResult{ID: index, MediaType: mediaType, Title: fmt.Sprint(page, index)})
		}
		json.NewEncoder(writer).Encode(SearchResponse{Results: results, TotalPages: 5})
	}))
	defer server.Close()

	client := NewClient(func() (string, string) { return "key", "en-US" }).WithBaseURL(server.URL)
	results, err := client.SearchMulti(context.Background(), "the", 40)
	if err != nil {
		t.Fatal(err)
	}
	if len(results) != 40 {
		t.Fatalf("expected 40 titles, got %d", len(results))
	}
	if len(requestedPages) != 3 {
		t.Fatalf("expected 3 pages (18 titles each), got %v", requestedPages)
	}
}
