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
	// MinYear and MaxYear bound the release (movies) or first air (series) year.
	MinYear int
	MaxYear int
	// MinRuntime and MaxRuntime are minutes.
	MinRuntime int
	MaxRuntime int
}

// DiscoverPage reads one page (from 1) of Discover and says how many pages there are.
func (c *Client) DiscoverPage(ctx context.Context, mediaType string, options DiscoverOptions, page int) ([]SearchResult, int, error) {
	params := options.params()
	addRangeParams(params, mediaType, options)
	params["page"] = strconv.Itoa(page)
	var response SearchResponse
	if err := c.get(ctx, discoverPath(mediaType), params, &response); err != nil {
		return nil, 0, err
	}
	return response.Results, response.TotalPages, nil
}

func discoverPath(mediaType string) string {
	if mediaType == "series" {
		return "/discover/tv"
	}
	return "/discover/movie"
}

// addRangeParams adds the year and runtime bounds; movies and series name their dates differently.
func addRangeParams(params map[string]string, mediaType string, options DiscoverOptions) {
	dateField := "primary_release_date"
	if mediaType == "series" {
		dateField = "first_air_date"
	}
	if options.MinYear > 0 {
		params[dateField+".gte"] = strconv.Itoa(options.MinYear) + "-01-01"
	}
	if options.MaxYear > 0 {
		params[dateField+".lte"] = strconv.Itoa(options.MaxYear) + "-12-31"
	}
	if options.MinRuntime > 0 {
		params["with_runtime.gte"] = strconv.Itoa(options.MinRuntime)
	}
	if options.MaxRuntime > 0 {
		params["with_runtime.lte"] = strconv.Itoa(options.MaxRuntime)
	}
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
	path := discoverPath(mediaType)
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
