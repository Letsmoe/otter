package tmdb

import (
	"context"
	"fmt"
	"strconv"
)

// detailAppend pulls everything the library loop needs in one request.
const detailAppend = "credits,videos,images,keywords,recommendations"

// SearchMovies returns TMDB movie matches for a query, best first, capped at
// limit (0 = first page only). Used by the live search to offer distinct, tmdb-keyed
// results that resolve to sources via plugins on selection.
func (c *Client) SearchMovies(ctx context.Context, query string, limit int) ([]SearchResult, error) {
	if query == "" {
		return nil, nil
	}
	return c.searchPages(ctx, "/search/movie", query, limit, nil)
}

// SearchMulti returns TMDB movie and series matches for a query in one
// relevance order (people are dropped), capped at limit (0 = first page only).
func (c *Client) SearchMulti(ctx context.Context, query string, limit int) ([]SearchResult, error) {
	if query == "" {
		return nil, nil
	}
	isTitle := func(result SearchResult) bool {
		return result.MediaType == "movie" || result.MediaType == "tv"
	}
	return c.searchPages(ctx, "/search/multi", query, limit, isTitle)
}

// maxSearchPages caps how many TMDB result pages (20 hits each) one search reads.
const maxSearchPages = 3

// searchPages reads result pages until limit hits are collected (0 = one
// page), TMDB runs out, or maxSearchPages is reached. keep drops hits when set.
func (c *Client) searchPages(ctx context.Context, path, query string, limit int, keep func(SearchResult) bool) ([]SearchResult, error) {
	results := []SearchResult{}
	for page := 1; page <= maxSearchPages; page++ {
		var resp SearchResponse
		params := map[string]string{"query": query, "page": strconv.Itoa(page)}
		if err := c.get(ctx, path, params, &resp); err != nil {
			return nil, err
		}
		for _, result := range resp.Results {
			if keep == nil || keep(result) {
				results = append(results, result)
			}
		}
		if limit <= 0 || len(results) >= limit || page >= resp.TotalPages {
			break
		}
	}
	if limit > 0 && len(results) > limit {
		return results[:limit], nil
	}
	return results, nil
}

// SearchSeries returns TMDB series matches for a query, best first, capped at
// limit (0 = first page only). Mirrors SearchMovies for the live series search.
func (c *Client) SearchSeries(ctx context.Context, query string, limit int) ([]SearchResult, error) {
	if query == "" {
		return nil, nil
	}
	return c.searchPages(ctx, "/search/tv", query, limit, nil)
}

// Recommendations returns TMDB's "recommended" titles for a movie or series.
// mediaType is "movie" or "series" (mapped to the /movie or /tv endpoint).
func (c *Client) Recommendations(ctx context.Context, mediaType string, tmdbID int) ([]SearchResult, error) {
	path := fmt.Sprintf("/movie/%d/recommendations", tmdbID)
	if mediaType == "series" {
		path = fmt.Sprintf("/tv/%d/recommendations", tmdbID)
	}
	var resp SearchResponse
	if err := c.get(ctx, path, nil, &resp); err != nil {
		return nil, err
	}
	return resp.Results, nil
}

// Trending returns this week's trending movies or series — the fallback pool for
// the TMDB recommendation rail when the library has no seeds yet.
func (c *Client) Trending(ctx context.Context, mediaType string) ([]SearchResult, error) {
	window := "movie"
	if mediaType == "series" {
		window = "tv"
	}
	var resp SearchResponse
	if err := c.get(ctx, "/trending/"+window+"/week", nil, &resp); err != nil {
		return nil, err
	}
	return resp.Results, nil
}

// detailParams requests images and videos in English, the configured
// language and language-neutral, so posters, logos and trailers exist.
func detailParams(appendToResponse string) map[string]string {
	return map[string]string{
		"append_to_response":     appendToResponse,
		"include_image_language": "en,de,null",
		"include_video_language": "en,de,null",
	}
}

// FetchMovieDetail loads a movie with credits, videos, images, keywords and
// TMDB's recommendations.
func (c *Client) FetchMovieDetail(ctx context.Context, tmdbID int) (*MovieDetail, error) {
	var detail MovieDetail
	path := fmt.Sprintf("/movie/%d", tmdbID)
	if err := c.get(ctx, path, detailParams(detailAppend), &detail); err != nil {
		return nil, err
	}
	return &detail, nil
}

// FetchSeriesDetail loads a series with its season list plus the same
// appended data as movies and the external ids (for the IMDb id).
func (c *Client) FetchSeriesDetail(ctx context.Context, tmdbID int) (*SeriesDetail, error) {
	var detail SeriesDetail
	path := fmt.Sprintf("/tv/%d", tmdbID)
	if err := c.get(ctx, path, detailParams(detailAppend+",external_ids"), &detail); err != nil {
		return nil, err
	}
	return &detail, nil
}

// FetchSeasonDetail loads a season's episodes. The endpoint is keyed by the
// parent series' TMDB id plus the season number.
func (c *Client) FetchSeasonDetail(ctx context.Context, seriesTMDBID, seasonNumber int) (*SeasonDetail, error) {
	var detail SeasonDetail
	path := fmt.Sprintf("/tv/%d/season/%d", seriesTMDBID, seasonNumber)
	if err := c.get(ctx, path, nil, &detail); err != nil {
		return nil, err
	}
	return &detail, nil
}

// NewAndUpcoming returns movies in or coming to cinemas, or series currently
// airing new episodes.
func (c *Client) NewAndUpcoming(ctx context.Context, mediaType string) ([]SearchResult, error) {
	paths := []string{"/movie/upcoming", "/movie/now_playing"}
	if mediaType == "series" {
		paths = []string{"/tv/on_the_air"}
	}
	results := []SearchResult{}
	for _, path := range paths {
		var resp SearchResponse
		if err := c.get(ctx, path, nil, &resp); err != nil {
			return nil, err
		}
		results = append(results, resp.Results...)
	}
	return results, nil
}
