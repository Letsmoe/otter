package letterboxd

import (
	"context"
	"errors"
	"sync"
	"sync/atomic"
	"time"

	"github.com/Letsmoe/otter/internal/library"
)

const (
	// Films matched at once; TMDB tolerates far more, this keeps a phone's backend calm.
	matchWorkers = 4
	// Film pages read for films TMDB search could not place.
	pageLookupBudget = 50
	// An import of a very large diary may take a while; it stops after this.
	importTimeout = 2 * time.Hour
	// How many unmatched films the status lists.
	maxListedNotMatched = 300
)

// ImportState is where an import stands.
type ImportState string

const (
	ImportIdle    ImportState = "idle"
	ImportRunning ImportState = "running"
	ImportDone    ImportState = "done"
	ImportFailed  ImportState = "failed"
)

// NotMatched is a film of the export TMDB had no match for.
type NotMatched struct {
	Film
	Reason string
}

// ImportStatus is the progress and result of a profile's latest import.
type ImportStatus struct {
	State ImportState
	// Total films in the export, and how many were looked up so far.
	Total     int
	Processed int
	// What the import added; titles Otter already had are not counted.
	Watched     int
	Rated       int
	Watchlisted int
	// Matched is how many films found their TMDB movie.
	Matched    int
	NotMatched []NotMatched
	// UnmatchedCount can be more than the listed ones.
	UnmatchedCount int
	Error          string
}

// ErrImportRunning is returned when a profile already has an import going.
var ErrImportRunning = errors.New("an import is already running for this profile")

// ImportOf returns the status of the profile's latest import.
func (s *Service) ImportOf(profileID int) ImportStatus {
	s.mutex.Lock()
	defer s.mutex.Unlock()
	status, found := s.imports[profileID]
	if !found {
		return ImportStatus{State: ImportIdle}
	}
	return *status
}

// StartImport reads an export ZIP and applies it to the profile in the
// background; watch the progress with ImportOf. A file that is no export is
// refused at once.
func (s *Service) StartImport(profileID int, zipBytes []byte) (ImportStatus, error) {
	export, err := ReadExport(zipBytes)
	if err != nil {
		return ImportStatus{}, err
	}
	films := export.Films()
	if err := s.beginImport(profileID, len(films)); err != nil {
		return ImportStatus{}, err
	}
	go s.runImport(profileID, export, films)
	return s.ImportOf(profileID), nil
}

func (s *Service) beginImport(profileID, total int) error {
	s.mutex.Lock()
	defer s.mutex.Unlock()
	current, found := s.imports[profileID]
	if found && current.State == ImportRunning {
		return ErrImportRunning
	}
	s.imports[profileID] = &ImportStatus{State: ImportRunning, Total: total}
	return nil
}

func (s *Service) updateImport(profileID int, change func(*ImportStatus)) {
	s.mutex.Lock()
	defer s.mutex.Unlock()
	change(s.imports[profileID])
}

func (s *Service) runImport(profileID int, export *Export, films []Film) {
	ctx, cancel := context.WithTimeout(context.Background(), importTimeout)
	defer cancel()
	matches := s.matchFilms(ctx, profileID, films)
	if ctx.Err() != nil {
		s.failImport(profileID, "the import took too long and was stopped")
		return
	}
	if err := s.applyExport(ctx, profileID, export, matches.refs); err != nil {
		s.failImport(profileID, err.Error())
		return
	}
	s.updateImport(profileID, func(status *ImportStatus) {
		status.State = ImportDone
		status.Matched = len(matches.refs)
		status.UnmatchedCount = len(matches.unmatched)
		status.NotMatched = listedNotMatched(matches.unmatched)
	})
	s.changed()
}

func (s *Service) failImport(profileID int, message string) {
	s.updateImport(profileID, func(status *ImportStatus) {
		status.State = ImportFailed
		status.Error = message
	})
}

func listedNotMatched(unmatched []NotMatched) []NotMatched {
	if len(unmatched) > maxListedNotMatched {
		return unmatched[:maxListedNotMatched]
	}
	return unmatched
}

// filmMatches is what looking up an export's films found, by Film.Key.
type filmMatches struct {
	refs      map[string]library.MediaRef
	unmatched []NotMatched
}

// matchFilms looks every film up on TMDB, a few at a time.
func (s *Service) matchFilms(ctx context.Context, profileID int, films []Film) filmMatches {
	matches := filmMatches{refs: map[string]library.MediaRef{}}
	var mutex sync.Mutex
	pages := &pageBudget{}
	pages.remaining.Store(pageLookupBudget)
	queue := make(chan Film)
	var workers sync.WaitGroup
	for range matchWorkers {
		workers.Add(1)
		go func() {
			defer workers.Done()
			for film := range queue {
				ref, missing := s.matchFilm(ctx, film, pages)
				mutex.Lock()
				recordMatch(&matches, film, ref, missing)
				mutex.Unlock()
				s.updateImport(profileID, func(status *ImportStatus) { status.Processed++ })
			}
		}()
	}
	for _, film := range films {
		queue <- film
	}
	close(queue)
	workers.Wait()
	return matches
}

func recordMatch(matches *filmMatches, film Film, ref library.MediaRef, missing *NotMatched) {
	if missing != nil {
		matches.unmatched = append(matches.unmatched, *missing)
		return
	}
	matches.refs[film.Key()] = ref
}

// pageBudget limits how many film pages an import reads.
type pageBudget struct {
	remaining atomic.Int32
}

func (b *pageBudget) take() bool {
	return b.remaining.Add(-1) >= 0
}

// matchFilm finds the film's movie: TMDB search by title and year, then, for
// films it could not place, the TMDB id on the film's Letterboxd page.
func (s *Service) matchFilm(ctx context.Context, film Film, pages *pageBudget) (library.MediaRef, *NotMatched) {
	result, ok, err := Match(ctx, s.finder, film)
	if err != nil {
		return library.MediaRef{}, &NotMatched{Film: film, Reason: "TMDB did not answer"}
	}
	if ok {
		ref, err := s.catalog.SaveMovieStub(ctx, result)
		if err != nil {
			return library.MediaRef{}, &NotMatched{Film: film, Reason: "could not be saved"}
		}
		return ref, nil
	}
	ref, found := s.matchByPage(ctx, film, pages)
	if found {
		return ref, nil
	}
	return library.MediaRef{}, &NotMatched{Film: film, Reason: "no movie with this title and year on TMDB"}
}

func (s *Service) matchByPage(ctx context.Context, film Film, pages *pageBudget) (library.MediaRef, bool) {
	if film.URI == "" || !pages.take() {
		return library.MediaRef{}, false
	}
	tmdbID, ok := TMDBIDOfFilm(ctx, s.client, film.URI)
	if !ok {
		return library.MediaRef{}, false
	}
	ref, err := s.catalog.EnsureMovie(ctx, tmdbID)
	return ref, err == nil
}

// applyExport records the export's watches, ratings and watchlist for the
// matched films.
func (s *Service) applyExport(ctx context.Context, profileID int, export *Export, refs map[string]library.MediaRef) error {
	if err := s.applyWatched(ctx, profileID, export, refs); err != nil {
		return err
	}
	if err := s.applyRatings(ctx, profileID, export, refs); err != nil {
		return err
	}
	return s.applyWatchlist(ctx, profileID, export, refs)
}

func (s *Service) applyWatched(ctx context.Context, profileID int, export *Export, refs map[string]library.MediaRef) error {
	for key, days := range watchedDays(export) {
		ref, matched := refs[key]
		if !matched {
			continue
		}
		changed, err := s.activity.ImportWatched(ctx, profileID, ref, dateStamp(days.first), dateStamp(days.last))
		if err != nil {
			return err
		}
		if changed {
			s.updateImport(profileID, func(status *ImportStatus) { status.Watched++ })
		}
	}
	return nil
}

func (s *Service) applyRatings(ctx context.Context, profileID int, export *Export, refs map[string]library.MediaRef) error {
	for key, choice := range latestRatings(export) {
		ref, matched := refs[key]
		if !matched {
			continue
		}
		changed, err := s.activity.ImportRating(ctx, profileID, ref, OtterRating(choice.stars), dateStamp(choice.day))
		if err != nil {
			return err
		}
		if changed {
			s.updateImport(profileID, func(status *ImportStatus) { status.Rated++ })
		}
	}
	return nil
}

func (s *Service) applyWatchlist(ctx context.Context, profileID int, export *Export, refs map[string]library.MediaRef) error {
	for _, entry := range export.Watchlist {
		ref, matched := refs[entry.Key()]
		if !matched {
			continue
		}
		changed, err := s.activity.ImportWatchlist(ctx, profileID, ref, dateStamp(entry.Date))
		if err != nil {
			return err
		}
		if changed {
			s.updateImport(profileID, func(status *ImportStatus) { status.Watchlisted++ })
		}
	}
	return nil
}

// dayRange is the first and last day a film was watched (YYYY-MM-DD).
type dayRange struct {
	first string
	last  string
}

// watchedDays works out when each watched film was seen: the diary's days of
// watching (rewatches widen the range), else the day the film was marked
// watched.
func watchedDays(export *Export) map[string]dayRange {
	days := map[string]dayRange{}
	for _, entry := range export.Diary {
		widen(days, entry.Key(), diaryDay(entry))
	}
	for _, entry := range export.Watched {
		if _, inDiary := days[entry.Key()]; !inDiary {
			widen(days, entry.Key(), entry.Date)
		}
	}
	return days
}

func diaryDay(entry Entry) string {
	if entry.WatchedDate != "" {
		return entry.WatchedDate
	}
	return entry.Date
}

func widen(days map[string]dayRange, key, day string) {
	current, found := days[key]
	if !found {
		days[key] = dayRange{first: day, last: day}
		return
	}
	if day < current.first {
		current.first = day
	}
	if day > current.last {
		current.last = day
	}
	days[key] = current
}

// starsOnDay is a rating and the day it was given.
type starsOnDay struct {
	stars float64
	day   string
}

// latestRatings picks each film's newest rating from the ratings file and
// the diary's rated entries.
func latestRatings(export *Export) map[string]starsOnDay {
	latest := map[string]starsOnDay{}
	for _, entry := range export.Ratings {
		keepNewer(latest, entry.Key(), entry.Stars, entry.Date)
	}
	for _, entry := range export.Diary {
		keepNewer(latest, entry.Key(), entry.Stars, diaryDay(entry))
	}
	return latest
}

func keepNewer(latest map[string]starsOnDay, key string, stars float64, day string) {
	if stars == 0 {
		return
	}
	current, found := latest[key]
	if found && current.day >= day {
		return
	}
	latest[key] = starsOnDay{stars: stars, day: day}
}
