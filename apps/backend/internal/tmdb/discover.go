package tmdb

import (
	"context"
	"strconv"
)

// Catalog sorts for Discover.
const (
	SortPopular  = "popularity.desc"
	SortTopRated = "vote_average.desc"
	SortRevenue  = "revenue.desc"
	SortVotes    = "vote_count.desc"
)

// Titles with fewer votes are skipped so top-rated lists aren't led by obscure titles.
const minimumVotes = "300"

// DiscoverOptions narrows a Discover request; zero values are left out.
type DiscoverOptions struct {
	// GenreIDs joins with "," for all of them or "|" for any of them, as TMDB's with_genres does.
	GenreIDs  string
	Sort      string
	MinVotes  int
	MaxVotes  int
	MinRating float64
}

// Discover lists movies or series straight from TMDB's catalog, sorted by
// sort and limited to genreID when it isn't 0. pages are fetched in order.
func (c *Client) Discover(ctx context.Context, mediaType string, genreID int, sort string, pages int) ([]SearchResult, error) {
	options := DiscoverOptions{Sort: sort}
	if genreID != 0 {
		options.GenreIDs = strconv.Itoa(genreID)
	}
	return c.DiscoverWith(ctx, mediaType, options, pages)
}

// DiscoverWith is Discover with extra filters.
func (c *Client) DiscoverWith(ctx context.Context, mediaType string, options DiscoverOptions, pages int) ([]SearchResult, error) {
	path := "/discover/movie"
	if mediaType == "series" {
		path = "/discover/tv"
	}
	params := options.params()
	results := []SearchResult{}
	for page := 1; page <= pages; page++ {
		params["page"] = strconv.Itoa(page)
		var response SearchResponse
		if err := c.get(ctx, path, params, &response); err != nil {
			return nil, err
		}
		results = append(results, response.Results...)
		if page >= response.TotalPages {
			break
		}
	}
	return results, nil
}

func (options DiscoverOptions) params() map[string]string {
	minVotes := minimumVotes
	if options.MinVotes > 0 {
		minVotes = strconv.Itoa(options.MinVotes)
	}
	params := map[string]string{"sort_by": options.Sort, "vote_count.gte": minVotes, "include_adult": "false"}
	if options.GenreIDs != "" {
		params["with_genres"] = options.GenreIDs
	}
	if options.MaxVotes > 0 {
		params["vote_count.lte"] = strconv.Itoa(options.MaxVotes)
	}
	if options.MinRating > 0 {
		params["vote_average.gte"] = strconv.FormatFloat(options.MinRating, 'f', -1, 64)
	}
	return params
}
