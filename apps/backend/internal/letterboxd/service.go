package letterboxd

import (
	"context"
	"net/http"
	"sync"
	"time"

	"github.com/Letsmoe/otter/internal/db"
	"github.com/Letsmoe/otter/internal/library"
	"github.com/Letsmoe/otter/internal/tmdb"
)

// Catalog stores the movies activity is recorded for.
type Catalog interface {
	// SaveMovieStub stores a TMDB search hit and returns its movie.
	SaveMovieStub(ctx context.Context, result tmdb.SearchResult) (library.MediaRef, error)
	// EnsureMovie returns the movie with a TMDB id, fetching it when it is not stored yet.
	EnsureMovie(ctx context.Context, tmdbID int) (library.MediaRef, error)
}

// Activity records what a profile watched, rated and saved (profiles.Service).
type Activity interface {
	ImportWatched(ctx context.Context, profileID int, ref library.MediaRef, startedAt, watchedAt string) (bool, error)
	ImportRating(ctx context.Context, profileID int, ref library.MediaRef, rating float64, ratedAt string) (bool, error)
	ImportWatchlist(ctx context.Context, profileID int, ref library.MediaRef, addedAt string) (bool, error)
}

// Service imports Letterboxd exports and follows public diary feeds.
type Service struct {
	database *db.DB
	finder   MovieFinder
	catalog  Catalog
	activity Activity
	// client reaches letterboxd.com (the feed and film pages).
	client *http.Client

	mutex   sync.Mutex
	imports map[int]*ImportStatus
	// onChange runs after an import or a sync recorded something (Neoworks syncs it).
	onChange func()
}

// SetOnChange sets what runs after an import or a sync recorded something.
func (s *Service) SetOnChange(onChange func()) {
	s.onChange = onChange
}

func (s *Service) changed() {
	if s.onChange != nil {
		s.onChange()
	}
}

func NewService(database *db.DB, finder MovieFinder, catalog Catalog, activity Activity, client *http.Client) *Service {
	return &Service{
		database: database,
		finder:   finder,
		catalog:  catalog,
		activity: activity,
		client:   client,
		imports:  map[int]*ImportStatus{},
	}
}

// libraryCatalog stores movies in the library.
type libraryCatalog struct {
	library *library.Service
}

// NewLibraryCatalog is the Catalog backed by the library.
func NewLibraryCatalog(service *library.Service) Catalog {
	return libraryCatalog{library: service}
}

func (c libraryCatalog) SaveMovieStub(ctx context.Context, result tmdb.SearchResult) (library.MediaRef, error) {
	return c.library.SaveMovieStub(ctx, result)
}

func (c libraryCatalog) EnsureMovie(ctx context.Context, tmdbID int) (library.MediaRef, error) {
	ref := library.MediaRef{MediaType: library.MediaTypeMovie, MediaID: tmdbID}
	stored, err := c.library.Store.Title(ctx, ref)
	if err != nil || stored != nil {
		return ref, err
	}
	_, err = c.library.EnsureTitle(ctx, ref)
	return ref, err
}

// dateStamp turns a day (YYYY-MM-DD) into the timestamp format the database
// uses, at noon so the day holds in every time zone; an unreadable day is now.
func dateStamp(day string) string {
	if _, err := time.Parse("2006-01-02", day); err != nil {
		return db.Now()
	}
	return day + "T12:00:00.000Z"
}
