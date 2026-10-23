package library

import (
	"context"
	"fmt"
	"log"
	"strconv"
	"sync"
	"time"

	"golang.org/x/sync/singleflight"

	"github.com/neoworks/otter/internal/db"
	"github.com/neoworks/otter/internal/hooks"
	"github.com/neoworks/otter/internal/plugins"
	"github.com/neoworks/otter/internal/tmdb"
)

// How long fetched data counts as fresh before it is fetched again.
const (
	titleDetailTTL = 7 * 24 * time.Hour
	streamsTTL     = 12 * time.Hour
	// Finding nothing is usually temporary (VPN down, a site hiccup).
	emptyStreamsTTL = 10 * time.Minute
)

// Service runs the library loop. Reads go straight to the Store; the
// Ensure*/FindStreams methods fetch whatever is missing or stale first.
type Service struct {
	Store   *Store
	tmdb    *tmdb.Client
	plugins *plugins.Registry
	runner  *plugins.Runner
	hooks   *hooks.Runner
	// streamsAllowed gates plugin lookups (the VPN requirement).
	streamsAllowed func() error
	// StreamUpdates announces each plugin's stream results as they arrive.
	StreamUpdates *Broker
	inflight      singleflight.Group
	// Titles whose plugins are being asked right now.
	lookups      map[MediaRef]bool
	lookupsMutex sync.Mutex
	// background bounds the follow-up fetches (filmographies, stale streams).
	background chan struct{}
}

func NewService(store *Store, tmdbClient *tmdb.Client, registry *plugins.Registry, runner *plugins.Runner, hookRunner *hooks.Runner, streamsAllowed func() error) *Service {
	return &Service{
		Store:          store,
		tmdb:           tmdbClient,
		plugins:        registry,
		runner:         runner,
		hooks:          hookRunner,
		streamsAllowed: streamsAllowed,
		StreamUpdates:  NewBroker(),
		background:     make(chan struct{}, 4),
		lookups:        map[MediaRef]bool{},
	}
}

// EnsureTitle returns a movie or series, fetching its TMDB detail first when
// it is missing or stale. Opening a title also fetches, in the background,
// other work of its director and leads (see relateByPeople).
func (s *Service) EnsureTitle(ctx context.Context, ref MediaRef) (*Title, error) {
	stored, err := s.Store.Title(ctx, ref)
	if err != nil {
		return nil, err
	}
	if stored == nil || isStale(stored.DetailFetchedAt, titleDetailTTL) {
		if err := s.fetchTitleOnce(ctx, ref, stored == nil || stored.DetailFetchedAt == ""); err != nil {
			if stored != nil {
				log.Printf("library: refresh %s %d: %v", ref.MediaType, ref.MediaID, err)
				return stored, nil
			}
			return nil, err
		}
		stored, err = s.Store.Title(ctx, ref)
		if err != nil {
			return nil, err
		}
	}
	s.followUp(ref)
	return stored, nil
}

// fetchTitleOnce collapses concurrent fetches of the same title into one.
func (s *Service) fetchTitleOnce(ctx context.Context, ref MediaRef, isNew bool) error {
	key := fmt.Sprintf("title:%s:%d", ref.MediaType, ref.MediaID)
	_, err, _ := s.inflight.Do(key, func() (any, error) {
		return nil, s.fetchTitle(context.WithoutCancel(ctx), ref, isNew)
	})
	return err
}

func (s *Service) fetchTitle(ctx context.Context, ref MediaRef, isNew bool) error {
	var title Title
	var err error
	switch ref.MediaType {
	case MediaTypeMovie:
		title, err = s.fetchMovie(ctx, ref.MediaID)
	case MediaTypeSeries:
		title, err = s.fetchSeries(ctx, ref.MediaID)
	default:
		return fmt.Errorf("not a title type: %q", ref.MediaType)
	}
	if err != nil {
		return err
	}
	if isNew {
		// Payload: the stored title.
		s.hooks.Emit(hooks.EventTitleAdded, title)
	}
	return nil
}

func (s *Service) fetchMovie(ctx context.Context, tmdbID int) (Title, error) {
	detail, err := s.tmdb.FetchMovieDetail(ctx, tmdbID)
	if err != nil {
		return Title{}, err
	}
	title := movieTitle(detail)
	if err := s.Store.SaveMovie(ctx, title); err != nil {
		return Title{}, err
	}
	ref := MediaRef{MediaType: MediaTypeMovie, MediaID: tmdbID}
	if err := s.Store.ReplaceCredits(ctx, ref, titleCredits(detail.Credits)); err != nil {
		return Title{}, err
	}
	return title, s.saveRecommendations(ctx, ref, MediaTypeMovie, detail.Recommendations.Results)
}

func (s *Service) fetchSeries(ctx context.Context, tmdbID int) (Title, error) {
	detail, err := s.tmdb.FetchSeriesDetail(ctx, tmdbID)
	if err != nil {
		return Title{}, err
	}
	title := seriesTitle(detail)
	if err := s.Store.SaveSeries(ctx, title); err != nil {
		return Title{}, err
	}
	if err := s.Store.SaveSeasons(ctx, seasonRows(tmdbID, detail.Seasons)); err != nil {
		return Title{}, err
	}
	ref := MediaRef{MediaType: MediaTypeSeries, MediaID: tmdbID}
	if err := s.Store.ReplaceCredits(ctx, ref, titleCredits(detail.Credits)); err != nil {
		return Title{}, err
	}
	return title, s.saveRecommendations(ctx, ref, MediaTypeSeries, detail.Recommendations.Results)
}

// saveRecommendations stores TMDB's recommendations for a title as stubs
// plus 'tmdb' related edges.
func (s *Service) saveRecommendations(ctx context.Context, from MediaRef, mediaType MediaType, results []tmdb.SearchResult) error {
	refs, err := s.saveStubs(ctx, mediaType, results)
	if err != nil {
		return err
	}
	return s.Store.ReplaceRelated(ctx, from, RelatedSourceTMDB, refs)
}

// saveStubs stores list entries as stub titles and returns their refs.
func (s *Service) saveStubs(ctx context.Context, mediaType MediaType, results []tmdb.SearchResult) ([]MediaRef, error) {
	refs := make([]MediaRef, 0, len(results))
	for _, result := range results {
		stub := stubTitle(mediaType, result, s.tmdb.GenreNames(ctx, result.GenreIDs))
		if err := s.Store.SaveStub(ctx, stub); err != nil {
			return nil, err
		}
		refs = append(refs, MediaRef{MediaType: mediaType, MediaID: result.ID})
	}
	return refs, nil
}

// EnsureSeason returns a season's episodes, fetching them from TMDB when the
// season was never fetched or (for running series) its data is a day old.
func (s *Service) EnsureSeason(ctx context.Context, seriesID, seasonNumber int) ([]Episode, error) {
	season, err := s.Store.Season(ctx, seriesID, seasonNumber)
	if err != nil {
		return nil, err
	}
	if season == nil || isStale(season.DetailFetchedAt, 24*time.Hour) {
		key := fmt.Sprintf("season:%d:%d", seriesID, seasonNumber)
		_, err, _ := s.inflight.Do(key, func() (any, error) {
			return nil, s.fetchSeason(context.WithoutCancel(ctx), seriesID, seasonNumber)
		})
		if err != nil && season == nil {
			return nil, err
		}
	}
	return s.Store.Episodes(ctx, seriesID, seasonNumber)
}

func (s *Service) fetchSeason(ctx context.Context, seriesID, seasonNumber int) error {
	detail, err := s.tmdb.FetchSeasonDetail(ctx, seriesID, seasonNumber)
	if err != nil {
		return err
	}
	return s.Store.SaveEpisodes(ctx, seriesID, seasonNumber, episodeRows(seriesID, detail.Episodes))
}

// isStale reports whether a fetch timestamp is missing or older than ttl.
func isStale(fetchedAt string, ttl time.Duration) bool {
	if fetchedAt == "" {
		return true
	}
	fetched, err := time.Parse(db.TimeFormat, fetchedAt)
	if err != nil {
		return true
	}
	return time.Since(fetched) > ttl
}

// followUp fetches related-by-people titles for an opened title.
func (s *Service) followUp(ref MediaRef) {
	s.inBackground("related people of "+ref.MediaType.String()+" "+strconv.Itoa(ref.MediaID), func(ctx context.Context) error {
		return s.relateByPeople(ctx, ref)
	})
}

// inBackground runs job with a bounded number of concurrent jobs. A job
// that cannot get a slot right away is dropped; the next visit retries it.
func (s *Service) inBackground(name string, job func(ctx context.Context) error) {
	select {
	case s.background <- struct{}{}:
	default:
		return
	}
	go func() {
		defer func() { <-s.background }()
		ctx, cancel := context.WithTimeout(context.Background(), 2*time.Minute)
		defer cancel()
		if err := job(ctx); err != nil {
			log.Printf("library: %s: %v", name, err)
		}
	}()
}

func (t MediaType) String() string {
	return string(t)
}
