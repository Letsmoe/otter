package library

import (
	"cmp"
	"context"
	"slices"

	"github.com/neoworks/otter/internal/tmdb"
)

// Search asks TMDB for titles matching query, stores the hits as stubs and
// returns them in TMDB's relevance order. An empty mediaType searches both.
func (s *Service) Search(ctx context.Context, query string, mediaType MediaType, limit int) ([]Title, error) {
	if mediaType != "" {
		return s.searchOneType(ctx, query, mediaType, limit)
	}
	results, err := s.tmdb.SearchMulti(ctx, query, limit)
	if err != nil {
		return nil, err
	}
	refs := []MediaRef{}
	for _, result := range results {
		resultType := MediaTypeMovie
		if result.MediaType == "tv" {
			resultType = MediaTypeSeries
		}
		found, err := s.saveStubs(ctx, resultType, []tmdb.SearchResult{result})
		if err != nil {
			return nil, err
		}
		refs = append(refs, found...)
	}
	return s.visibleTitles(ctx, refs)
}

func (s *Service) searchOneType(ctx context.Context, query string, mediaType MediaType, limit int) ([]Title, error) {
	results, err := s.searchTMDB(ctx, mediaType, query, limit)
	if err != nil {
		return nil, err
	}
	refs, err := s.saveStubs(ctx, mediaType, results)
	if err != nil {
		return nil, err
	}
	return s.visibleTitles(ctx, refs)
}

// visibleTitles loads refs in order, without adult titles.
func (s *Service) visibleTitles(ctx context.Context, refs []MediaRef) ([]Title, error) {
	titles, err := s.Store.Titles(ctx, refs)
	if err != nil {
		return nil, err
	}
	return slices.DeleteFunc(titles, func(title Title) bool { return title.Adult }), nil
}

// Trending returns this week's trending titles, stored as stubs.
func (s *Service) Trending(ctx context.Context, mediaType MediaType) ([]Title, error) {
	refs := []MediaRef{}
	for _, trendingType := range searchTypes(mediaType) {
		results, err := s.tmdb.Trending(ctx, string(trendingType))
		if err != nil {
			return nil, err
		}
		found, err := s.saveStubs(ctx, trendingType, results)
		if err != nil {
			return nil, err
		}
		refs = append(refs, found...)
	}
	return s.sortedTitles(ctx, refs, 0)
}

func searchTypes(mediaType MediaType) []MediaType {
	if mediaType == "" {
		return []MediaType{MediaTypeMovie, MediaTypeSeries}
	}
	return []MediaType{mediaType}
}

func (s *Service) searchTMDB(ctx context.Context, mediaType MediaType, query string, limit int) ([]tmdb.SearchResult, error) {
	if mediaType == MediaTypeSeries {
		return s.tmdb.SearchSeries(ctx, query, limit)
	}
	return s.tmdb.SearchMovies(ctx, query, limit)
}

// sortedTitles loads refs, most popular first, capped at limit (0 = all).
func (s *Service) sortedTitles(ctx context.Context, refs []MediaRef, limit int) ([]Title, error) {
	titles, err := s.Store.Titles(ctx, refs)
	if err != nil {
		return nil, err
	}
	titles = slices.DeleteFunc(titles, func(title Title) bool { return title.Adult })
	slices.SortStableFunc(titles, func(left, right Title) int {
		return cmp.Compare(right.Popularity, left.Popularity)
	})
	if limit > 0 && len(titles) > limit {
		titles = titles[:limit]
	}
	return titles, nil
}
