package library

import (
	"context"
	"slices"
	"strconv"

	"github.com/Letsmoe/otter/internal/tmdb"
)

// Discover sorts.
const (
	DiscoverSortPopular  = "popular"
	DiscoverSortTopRated = "top_rated"
	DiscoverSortNewest   = "newest"
)

// discoverMinVotes keeps unrated filler out of browsing; top-rated sorting uses TMDB's stricter default.
const discoverMinVotes = 50

// DiscoverFilter narrows Discover. Zero values don't filter.
type DiscoverFilter struct {
	Genre      string
	MinYear    int
	MaxYear    int
	MinRating  float64
	MinRuntime int
	MaxRuntime int
	MinSeasons int
	MaxSeasons int
	// Sort is one of the DiscoverSort constants; it only applies without a query.
	Sort string
}

// TitlePage is one page of results; HasMore says whether a next page exists.
type TitlePage struct {
	Titles  []Title
	HasMore bool
}

// Discover pages through movies or series: TMDB's search results for query,
// or without one TMDB's catalog, narrowed by filter. Pages count from 1; a
// filtered search page can come back short or empty while more follow.
func (s *Service) Discover(ctx context.Context, mediaType MediaType, query string, filter DiscoverFilter, page int) (TitlePage, error) {
	if page < 1 {
		page = 1
	}
	if query != "" {
		return s.searchPage(ctx, mediaType, query, filter, page)
	}
	return s.catalogPage(ctx, mediaType, filter, page)
}

// GenresOf lists the genre names TMDB has for movies or series.
func (s *Service) GenresOf(ctx context.Context, mediaType MediaType) ([]string, error) {
	genres, err := s.tmdb.GenresOf(ctx, string(mediaType))
	if err != nil {
		return nil, err
	}
	names := make([]string, 0, len(genres))
	for _, genre := range genres {
		names = append(names, genre.Name)
	}
	return names, nil
}

func (s *Service) catalogPage(ctx context.Context, mediaType MediaType, filter DiscoverFilter, page int) (TitlePage, error) {
	options, err := s.discoverOptions(ctx, mediaType, filter)
	if err != nil {
		return TitlePage{}, err
	}
	results, totalPages, err := s.tmdb.DiscoverPage(ctx, string(mediaType), options, page)
	if err != nil {
		return TitlePage{}, err
	}
	titles, err := s.storedPage(ctx, mediaType, results)
	if err != nil {
		return TitlePage{}, err
	}
	// Discover can't count seasons; those filter the page here.
	titles, err = s.FilterTitles(ctx, titles, TitleFilter{MinSeasons: filter.MinSeasons, MaxSeasons: filter.MaxSeasons})
	return TitlePage{Titles: titles, HasMore: page < totalPages}, err
}

func (s *Service) searchPage(ctx context.Context, mediaType MediaType, query string, filter DiscoverFilter, page int) (TitlePage, error) {
	results, totalPages, err := s.tmdb.SearchPage(ctx, string(mediaType), query, page)
	if err != nil {
		return TitlePage{}, err
	}
	titles, err := s.storedPage(ctx, mediaType, results)
	if err != nil {
		return TitlePage{}, err
	}
	titles = slices.DeleteFunc(titles, func(title Title) bool { return !matchesListFields(title, filter) })
	titles, err = s.FilterTitles(ctx, titles, detailFilter(filter))
	return TitlePage{Titles: titles, HasMore: page < totalPages}, err
}

// storedPage saves TMDB list results as stubs and loads them back in order, without adult titles.
func (s *Service) storedPage(ctx context.Context, mediaType MediaType, results []tmdb.SearchResult) ([]Title, error) {
	refs, err := s.saveStubs(ctx, mediaType, results)
	if err != nil {
		return nil, err
	}
	return s.visibleTitles(ctx, uniqueRefs(refs))
}

func (s *Service) discoverOptions(ctx context.Context, mediaType MediaType, filter DiscoverFilter) (tmdb.DiscoverOptions, error) {
	options := tmdb.DiscoverOptions{
		Sort:       discoverSort(mediaType, filter.Sort),
		MinVotes:   discoverMinVotes,
		MinRating:  filter.MinRating,
		MinYear:    filter.MinYear,
		MaxYear:    filter.MaxYear,
		MinRuntime: filter.MinRuntime,
		MaxRuntime: filter.MaxRuntime,
	}
	if filter.Sort == DiscoverSortTopRated {
		options.MinVotes = 0
	}
	if filter.Genre == "" {
		return options, nil
	}
	genreID, err := s.genreIDOf(ctx, mediaType, filter.Genre)
	if err != nil {
		return options, err
	}
	options.GenreIDs = genreID
	return options, nil
}

// genreIDOf finds the TMDB id of a genre name; an unknown name matches nothing rather than everything.
func (s *Service) genreIDOf(ctx context.Context, mediaType MediaType, name string) (string, error) {
	genres, err := s.tmdb.GenresOf(ctx, string(mediaType))
	if err != nil {
		return "", err
	}
	for _, genre := range genres {
		if genre.Name == name {
			return strconv.Itoa(genre.ID), nil
		}
	}
	return "-1", nil
}

func discoverSort(mediaType MediaType, sort string) string {
	switch sort {
	case DiscoverSortTopRated:
		return tmdb.SortTopRated
	case DiscoverSortNewest:
		if mediaType == MediaTypeSeries {
			return "first_air_date.desc"
		}
		return "primary_release_date.desc"
	}
	return tmdb.SortPopular
}

// matchesListFields checks what search results already carry: genres and the year.
func matchesListFields(title Title, filter DiscoverFilter) bool {
	if filter.Genre != "" && !slices.Contains(title.Genres, filter.Genre) {
		return false
	}
	year := title.Year()
	if filter.MinYear > 0 && year < filter.MinYear {
		return false
	}
	if filter.MaxYear > 0 && (year == 0 || year > filter.MaxYear) {
		return false
	}
	return true
}

func detailFilter(filter DiscoverFilter) TitleFilter {
	return TitleFilter{
		MinRating:  filter.MinRating,
		MinRuntime: filter.MinRuntime,
		MaxRuntime: filter.MaxRuntime,
		MinSeasons: filter.MinSeasons,
		MaxSeasons: filter.MaxSeasons,
	}
}
