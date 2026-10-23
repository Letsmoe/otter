package library

import (
	"context"
	"sync"

	"github.com/neoworks/otter/internal/tmdb"
)

// CatalogRow is one genre's popular titles, straight from TMDB.
type CatalogRow struct {
	Genre  string
	Titles []Title
}

// Catalog sorts.
const (
	CatalogPopular  = "popular"
	CatalogTopRated = "top_rated"
)

const catalogPages = 2

// TMDB genre ids for Home's catalog rows, most broadly liked first. Ids, not
// names, so the rows work in every TMDB language.
var catalogGenreIDs = map[MediaType][]int{
	MediaTypeMovie:  {28, 35, 53, 18, 878, 27, 10749, 16, 12, 80, 14, 99},
	MediaTypeSeries: {18, 35, 80, 10765, 10759, 9648, 16, 99, 10764, 10751},
}

// Catalog returns popular or top-rated titles from TMDB's catalog, stored as
// stubs, in TMDB's order. It fills Home before the profile has history.
func (s *Service) Catalog(ctx context.Context, mediaType MediaType, sort string) ([]Title, error) {
	tmdbSort := tmdb.SortPopular
	if sort == CatalogTopRated {
		tmdbSort = tmdb.SortTopRated
	}
	return s.discover(ctx, mediaType, 0, tmdbSort)
}

// CatalogRows returns up to count genre rows of popular titles, skipping the
// genres named in exclude (rows Home already shows).
func (s *Service) CatalogRows(ctx context.Context, mediaType MediaType, count int, exclude []string) ([]CatalogRow, error) {
	genreIDs := s.catalogGenres(ctx, mediaType, count, exclude)
	rows := make([]CatalogRow, len(genreIDs))
	errs := make([]error, len(genreIDs))
	var wait sync.WaitGroup
	for index, genreID := range genreIDs {
		wait.Add(1)
		go func() {
			defer wait.Done()
			titles, err := s.discover(ctx, mediaType, genreID, tmdb.SortPopular)
			rows[index] = CatalogRow{Genre: s.genreName(ctx, genreID), Titles: titles}
			errs[index] = err
		}()
	}
	wait.Wait()
	for _, err := range errs {
		if err != nil {
			return nil, err
		}
	}
	return rows, nil
}

func (s *Service) catalogGenres(ctx context.Context, mediaType MediaType, count int, exclude []string) []int {
	excluded := map[string]bool{}
	for _, name := range exclude {
		excluded[name] = true
	}
	chosen := []int{}
	for _, genreID := range catalogGenreIDs[mediaType] {
		if len(chosen) == count {
			break
		}
		if excluded[s.genreName(ctx, genreID)] {
			continue
		}
		chosen = append(chosen, genreID)
	}
	return chosen
}

func (s *Service) genreName(ctx context.Context, genreID int) string {
	names := s.tmdb.GenreNames(ctx, []int{genreID})
	if len(names) == 0 {
		return ""
	}
	return names[0]
}

func (s *Service) discover(ctx context.Context, mediaType MediaType, genreID int, sort string) ([]Title, error) {
	results, err := s.tmdb.Discover(ctx, string(mediaType), genreID, sort, catalogPages)
	if err != nil {
		return nil, err
	}
	refs, err := s.saveStubs(ctx, mediaType, results)
	if err != nil {
		return nil, err
	}
	return s.visibleTitles(ctx, uniqueRefs(refs))
}

// Catalog themes: curated discover queries behind Home's themed rows.
const (
	CatalogThemeHiddenGems   = "hidden_gems"
	CatalogThemeBlockbusters = "blockbusters"
	CatalogThemeFeelGood     = "feel_good"
	CatalogThemeBingeworthy  = "bingeworthy"
)

// TMDB genre ids; comedy and family are joined with "|" so either one counts.
const feelGoodGenres = "35|10751"

// themeOptions maps a theme to its TMDB query; ok is false when the theme
// doesn't exist for that media type (blockbusters are movies only).
func themeOptions(mediaType MediaType, theme string) (tmdb.DiscoverOptions, bool) {
	switch theme {
	case CatalogThemeHiddenGems:
		return tmdb.DiscoverOptions{Sort: tmdb.SortPopular, MinRating: 7.5, MinVotes: 200, MaxVotes: 2000}, true
	case CatalogThemeBlockbusters:
		return tmdb.DiscoverOptions{Sort: tmdb.SortRevenue}, mediaType == MediaTypeMovie
	case CatalogThemeFeelGood:
		return tmdb.DiscoverOptions{GenreIDs: feelGoodGenres, Sort: tmdb.SortPopular, MinRating: 7}, true
	case CatalogThemeBingeworthy:
		return tmdb.DiscoverOptions{Sort: tmdb.SortVotes, MinRating: 7.5, MinVotes: 1000}, mediaType == MediaTypeSeries
	}
	return tmdb.DiscoverOptions{}, false
}

// CatalogTheme returns the titles of one themed row, stored as stubs; empty
// when the theme doesn't apply to the media type.
func (s *Service) CatalogTheme(ctx context.Context, mediaType MediaType, theme string) ([]Title, error) {
	options, applies := themeOptions(mediaType, theme)
	if !applies {
		return []Title{}, nil
	}
	results, err := s.tmdb.DiscoverWith(ctx, string(mediaType), options, catalogPages)
	if err != nil {
		return nil, err
	}
	refs, err := s.saveStubs(ctx, mediaType, results)
	if err != nil {
		return nil, err
	}
	return s.visibleTitles(ctx, uniqueRefs(refs))
}
