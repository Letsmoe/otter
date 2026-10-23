package tmdb

import (
	"context"
	"sync"
)

// genreCache maps genre ids to names per response language. List endpoints
// (search, recommendations, filmographies) only return ids.
type genreCache struct {
	mutex      sync.Mutex
	byLanguage map[string]map[int]string
}

type genreListResponse struct {
	Genres []Genre `json:"genres"`
}

// GenreNames returns the names of the given genre ids. Unknown ids are skipped.
func (c *Client) GenreNames(ctx context.Context, ids []int) []string {
	names := c.genreTable(ctx)
	result := make([]string, 0, len(ids))
	for _, id := range ids {
		if name, ok := names[id]; ok {
			result = append(result, name)
		}
	}
	return result
}

// genreTable loads the movie and TV genre lists once per language.
func (c *Client) genreTable(ctx context.Context) map[int]string {
	language := c.language()
	c.genres.mutex.Lock()
	defer c.genres.mutex.Unlock()
	if cached, ok := c.genres.byLanguage[language]; ok {
		return cached
	}
	table := map[int]string{}
	for _, path := range []string{"/genre/movie/list", "/genre/tv/list"} {
		var response genreListResponse
		if err := c.get(ctx, path, nil, &response); err != nil {
			return table // retried on the next call, nothing cached
		}
		for _, genre := range response.Genres {
			table[genre.ID] = genre.Name
		}
	}
	if c.genres.byLanguage == nil {
		c.genres.byLanguage = map[string]map[int]string{}
	}
	c.genres.byLanguage[language] = table
	return table
}
