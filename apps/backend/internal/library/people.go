package library

import (
	"context"
	"fmt"
	"sort"
	"time"

	"github.com/neoworks/otter/internal/tmdb"
)

const (
	RelatedSourceTMDB   = "tmdb"
	RelatedSourcePeople = "people"
)

const (
	personDetailTTL = 30 * 24 * time.Hour
	// filmographySize caps how many of a person's titles are stored.
	filmographySize = 40
	// relatedLeadActors is how many top-billed actors feed related titles.
	relatedLeadActors = 2
	// relatedPerPerson caps the related titles taken from one person.
	relatedPerPerson = 8
)

// EnsurePerson returns a person, fetching biography and filmography from
// TMDB first when missing or stale. The filmography lands as stub titles
// with credits, so it doubles as recommendation candidates.
func (s *Service) EnsurePerson(ctx context.Context, personID int) (*Person, error) {
	stored, err := s.Store.Person(ctx, personID)
	if err != nil {
		return nil, err
	}
	if stored != nil && !isStale(stored.DetailFetchedAt, personDetailTTL) {
		return stored, nil
	}
	key := fmt.Sprintf("person:%d", personID)
	_, err, _ = s.inflight.Do(key, func() (any, error) {
		_, fetchErr := s.fetchPerson(context.WithoutCancel(ctx), personID)
		return nil, fetchErr
	})
	if err != nil && stored == nil {
		return nil, err
	}
	return s.Store.Person(ctx, personID)
}

func (s *Service) fetchPerson(ctx context.Context, personID int) (*tmdb.PersonDetail, error) {
	detail, err := s.tmdb.FetchPersonDetail(ctx, personID)
	if err != nil {
		return nil, err
	}
	if err := s.Store.SavePerson(ctx, personRow(detail)); err != nil {
		return nil, err
	}
	for _, credit := range topCredits(detail, filmographySize) {
		if err := s.saveFilmographyCredit(ctx, personID, credit); err != nil {
			return nil, err
		}
	}
	return detail, nil
}

// personCredit is one filmography entry with the role it was credited for.
type personCredit struct {
	movie tmdb.PersonMovieCredit
	role  string
}

func (s *Service) saveFilmographyCredit(ctx context.Context, personID int, credit personCredit) error {
	result := tmdb.SearchResult{
		ID: credit.movie.ID, Title: credit.movie.Title, ReleaseDate: credit.movie.ReleaseDate,
		PosterPath: credit.movie.PosterPath, Popularity: credit.movie.Popularity,
		VoteAverage: credit.movie.VoteAverage, VoteCount: credit.movie.VoteCount,
		GenreIDs: credit.movie.GenreIDs,
		Adult:    credit.movie.Adult,
	}
	refs, err := s.saveStubs(ctx, MediaTypeMovie, []tmdb.SearchResult{result})
	if err != nil {
		return err
	}
	return s.Store.AddCredit(ctx, refs[0], Credit{
		PersonID: personID, Role: credit.role, Job: credit.movie.Job,
		Character: credit.movie.Character, Order: credit.movie.Order,
	})
}

// topCredits returns a person's most-voted movies: directing credits plus
// acting roles, without duplicates.
func topCredits(detail *tmdb.PersonDetail, limit int) []personCredit {
	credits := []personCredit{}
	seen := map[int]bool{}
	for _, movie := range detail.MovieCredits.Crew {
		if movie.Job == "Director" && !seen[movie.ID] {
			seen[movie.ID] = true
			credits = append(credits, personCredit{movie: movie, role: RoleCrew})
		}
	}
	for _, movie := range detail.MovieCredits.Cast {
		if !seen[movie.ID] {
			seen[movie.ID] = true
			credits = append(credits, personCredit{movie: movie, role: RoleCast})
		}
	}
	sort.SliceStable(credits, func(first, second int) bool {
		return credits[first].movie.VoteCount > credits[second].movie.VoteCount
	})
	if len(credits) > limit {
		credits = credits[:limit]
	}
	return credits
}

// relateByPeople links a title to the best other work of its director and
// lead actors ('people' related edges). Their filmographies are fetched as a
// side effect, which also fills the recommendation pool.
func (s *Service) relateByPeople(ctx context.Context, ref MediaRef) error {
	credits, err := s.Store.Credits(ctx, ref)
	if err != nil {
		return err
	}
	related := []MediaRef{}
	seen := map[MediaRef]bool{ref: true}
	for _, credit := range keyPeople(credits) {
		if _, err := s.EnsurePerson(ctx, credit.PersonID); err != nil {
			return err
		}
		filmography, err := s.Store.Filmography(ctx, credit.PersonID, "")
		if err != nil {
			return err
		}
		related = appendBestTitles(related, seen, filmography, relatedPerPerson)
	}
	return s.Store.ReplaceRelated(ctx, ref, RelatedSourcePeople, related)
}

// keyPeople picks the directors (or series creators) and lead actors.
func keyPeople(credits []Credit) []Credit {
	people := []Credit{}
	for _, credit := range credits {
		isLead := credit.Role == RoleCast && credit.Order < relatedLeadActors
		isAuthor := credit.Job == "Director" || credit.Job == "Creator"
		if isLead || isAuthor {
			people = append(people, credit)
		}
	}
	return people
}

// appendBestTitles adds up to limit unseen titles, highest rated first.
func appendBestTitles(related []MediaRef, seen map[MediaRef]bool, titles []Title, limit int) []MediaRef {
	sort.SliceStable(titles, func(first, second int) bool {
		return titles[first].VoteAverage*float64(titles[first].VoteCount+1) >
			titles[second].VoteAverage*float64(titles[second].VoteCount+1)
	})
	added := 0
	for _, title := range titles {
		ref := MediaRef{MediaType: title.MediaType, MediaID: title.TMDBID}
		if seen[ref] || added >= limit {
			continue
		}
		seen[ref] = true
		related = append(related, ref)
		added++
	}
	return related
}
