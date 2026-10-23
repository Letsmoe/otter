package library

import (
	"context"
	"slices"
	"strings"
	"time"
)

// NewAndComingSoon returns titles about to be released (soonest first)
// followed by recent releases (newest first), stored as stubs.
func (s *Service) NewAndComingSoon(ctx context.Context, mediaType MediaType) ([]Title, error) {
	results, err := s.tmdb.NewAndUpcoming(ctx, string(mediaType))
	if err != nil {
		return nil, err
	}
	refs, err := s.saveStubs(ctx, mediaType, results)
	if err != nil {
		return nil, err
	}
	titles, err := s.Store.Titles(ctx, uniqueRefs(refs))
	if err != nil {
		return nil, err
	}
	titles = slices.DeleteFunc(titles, func(title Title) bool { return title.Adult })
	sortByRelease(titles, time.Now().Format(time.DateOnly))
	return titles, nil
}

func uniqueRefs(refs []MediaRef) []MediaRef {
	seen := map[MediaRef]bool{}
	unique := make([]MediaRef, 0, len(refs))
	for _, ref := range refs {
		if seen[ref] {
			continue
		}
		seen[ref] = true
		unique = append(unique, ref)
	}
	return unique
}

// sortByRelease puts upcoming titles first, soonest first, then released
// ones, newest first. Dates are YYYY-MM-DD, so they compare as strings.
func sortByRelease(titles []Title, today string) {
	slices.SortStableFunc(titles, func(left, right Title) int {
		leftUpcoming := left.ReleaseDate > today
		rightUpcoming := right.ReleaseDate > today
		if leftUpcoming != rightUpcoming {
			if leftUpcoming {
				return -1
			}
			return 1
		}
		if leftUpcoming {
			return strings.Compare(left.ReleaseDate, right.ReleaseDate)
		}
		return strings.Compare(right.ReleaseDate, left.ReleaseDate)
	})
}
