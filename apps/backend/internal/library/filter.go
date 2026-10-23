package library

import (
	"context"
	"log"
	"sync"

	"github.com/neoworks/otter/internal/db"
)

// detailFetchConcurrency bounds parallel TMDB detail requests while filtering.
const detailFetchConcurrency = 6

// TitleFilter narrows a list of titles. Zero values don't filter.
type TitleFilter struct {
	MinRating  float64
	MinRuntime int
	MaxRuntime int
	MinSeasons int
	MaxSeasons int
}

// needsDetail reports whether the filter reads fields list results lack.
func (filter TitleFilter) needsDetail() bool {
	return filter.MinRuntime > 0 || filter.MaxRuntime > 0 || filter.MinSeasons > 0 || filter.MaxSeasons > 0
}

func (filter TitleFilter) isEmpty() bool {
	return filter.MinRating == 0 && !filter.needsDetail()
}

// FilterTitles keeps the titles matching filter, in order. Runtime and season
// filters first fetch the details of titles only known from lists.
func (s *Service) FilterTitles(ctx context.Context, titles []Title, filter TitleFilter) ([]Title, error) {
	if filter.isEmpty() {
		return titles, nil
	}
	if filter.needsDetail() {
		var err error
		titles, err = s.withDetail(ctx, titles)
		if err != nil {
			return nil, err
		}
	}
	matching := []Title{}
	for _, title := range titles {
		matches, err := s.matches(ctx, title, filter)
		if err != nil {
			return nil, err
		}
		if matches {
			matching = append(matching, title)
		}
	}
	return matching, nil
}

// withDetail fetches missing details in parallel and reloads the titles.
func (s *Service) withDetail(ctx context.Context, titles []Title) ([]Title, error) {
	refs := make([]MediaRef, 0, len(titles))
	slots := make(chan struct{}, detailFetchConcurrency)
	var wait sync.WaitGroup
	for _, title := range titles {
		ref := title.Ref()
		refs = append(refs, ref)
		if title.DetailFetchedAt != "" {
			continue
		}
		wait.Add(1)
		go func() {
			defer wait.Done()
			slots <- struct{}{}
			defer func() { <-slots }()
			if err := s.fetchTitleOnce(ctx, ref, true); err != nil {
				log.Printf("library: detail for filter %s %d: %v", ref.MediaType, ref.MediaID, err)
			}
		}()
	}
	wait.Wait()
	return s.Store.Titles(ctx, refs)
}

func (s *Service) matches(ctx context.Context, title Title, filter TitleFilter) (bool, error) {
	if title.VoteAverage < filter.MinRating {
		return false, nil
	}
	if !withinRange(title.Runtime, filter.MinRuntime, filter.MaxRuntime) {
		return false, nil
	}
	if filter.MinSeasons == 0 && filter.MaxSeasons == 0 {
		return true, nil
	}
	if title.MediaType != MediaTypeSeries {
		return false, nil
	}
	seasons, err := s.Store.SeasonCount(ctx, title.TMDBID)
	if err != nil {
		return false, err
	}
	return withinRange(seasons, filter.MinSeasons, filter.MaxSeasons), nil
}

// withinRange checks value against optional bounds; 0 means unbounded.
func withinRange(value, minimum, maximum int) bool {
	if minimum > 0 && value < minimum {
		return false
	}
	if maximum > 0 && value > maximum {
		return false
	}
	return true
}

// SeasonCount counts a series' regular seasons (specials excluded).
func (s *Store) SeasonCount(ctx context.Context, seriesID int) (int, error) {
	row, err := s.database.QueryRow(ctx, `SELECT COUNT(*) AS seasons FROM season
		WHERE series_id = $series_id AND season_number > 0`, db.Vars{"series_id": seriesID})
	if err != nil {
		return 0, err
	}
	return db.AsInt(row["seasons"]), nil
}
