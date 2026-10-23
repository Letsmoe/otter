package notify

import (
	"context"
	"log"
	"time"

	"github.com/neoworks/otter/internal/db"
	"github.com/neoworks/otter/internal/library"
)

const (
	sweepInterval   = 3 * time.Hour
	firstSweepDelay = time.Minute
	// Only the newest seasons can have new or upcoming episodes.
	seasonsChecked = 2
)

// follower is one profile having a title on its watchlist.
type follower struct {
	ProfileID int    `json:"profile_id"`
	AddedAt   string `json:"added_at"`
}

type watchlistRow struct {
	ProfileID int               `json:"profile_id"`
	MediaType library.MediaType `json:"media_type"`
	MediaID   int               `json:"media_id"`
	AddedAt   string            `json:"added_at"`
}

// Watcher periodically checks every watchlisted title and stores what is new.
type Watcher struct {
	store    *Store
	database *db.DB
	library  *library.Service
}

func NewWatcher(store *Store, database *db.DB, libraryService *library.Service) *Watcher {
	return &Watcher{store: store, database: database, library: libraryService}
}

// Run sweeps shortly after start and then every sweepInterval until ctx ends.
func (w *Watcher) Run(ctx context.Context) {
	timer := time.NewTimer(firstSweepDelay)
	defer timer.Stop()
	for {
		select {
		case <-ctx.Done():
			return
		case <-timer.C:
			w.Sweep(ctx)
			timer.Reset(sweepInterval)
		}
	}
}

// Sweep checks every title on any watchlist once.
func (w *Watcher) Sweep(ctx context.Context) {
	followed, err := w.followedTitles(ctx)
	if err != nil {
		log.Printf("notify: read watchlists: %v", err)
		return
	}
	today := startOfDay(time.Now())
	for ref, followers := range followed {
		if ctx.Err() != nil {
			return
		}
		notices, err := w.noticesFor(ctx, ref, today)
		if err != nil {
			log.Printf("notify: check %s %d: %v", ref.MediaType, ref.MediaID, err)
			continue
		}
		w.deliver(ctx, notices, followers)
	}
}

func startOfDay(moment time.Time) time.Time {
	year, month, day := moment.UTC().Date()
	return time.Date(year, month, day, 0, 0, 0, 0, time.UTC)
}

func (w *Watcher) followedTitles(ctx context.Context) (map[library.MediaRef][]follower, error) {
	rows, err := db.QueryAs[watchlistRow](ctx, w.database,
		"SELECT profile_id, media_type, media_id, added_at FROM watchlist", nil)
	if err != nil {
		return nil, err
	}
	followed := map[library.MediaRef][]follower{}
	for _, row := range rows {
		ref := library.MediaRef{MediaType: row.MediaType, MediaID: row.MediaID}
		followed[ref] = append(followed[ref], follower{ProfileID: row.ProfileID, AddedAt: row.AddedAt})
	}
	return followed, nil
}

func (w *Watcher) noticesFor(ctx context.Context, ref library.MediaRef, today time.Time) ([]Notice, error) {
	title, err := w.library.EnsureTitle(ctx, ref)
	if err != nil || title == nil {
		return nil, err
	}
	if ref.MediaType == library.MediaTypeSeries {
		return w.seriesNotices(ctx, *title, today)
	}
	return w.movieNotices(ctx, *title, today)
}

// movieNotices looks up links for released movies that have none yet.
func (w *Watcher) movieNotices(ctx context.Context, movie library.Title, today time.Time) ([]Notice, error) {
	stored, err := w.library.Store.Streams(ctx, movie.Ref())
	if err != nil {
		return nil, err
	}
	hadStreams := len(stored) > 0
	foundCount := 0
	if !hadStreams && isReleased(movie, today) {
		foundCount, err = w.lookUpStreams(ctx, movie.Ref())
		if err != nil {
			return nil, err
		}
	}
	return MovieNotices(movie, hadStreams, foundCount, today), nil
}

func isReleased(movie library.Title, today time.Time) bool {
	release, known := parseDate(movie.ReleaseDate)
	return known && !release.After(today)
}

func (w *Watcher) lookUpStreams(ctx context.Context, ref library.MediaRef) (int, error) {
	lookedUp, err := w.library.FindStreamsIfStale(ctx, ref)
	if err != nil || !lookedUp {
		return 0, err
	}
	found, err := w.library.Store.Streams(ctx, ref)
	return len(found), err
}

func (w *Watcher) seriesNotices(ctx context.Context, series library.Title, today time.Time) ([]Notice, error) {
	seasons, err := w.library.Store.Seasons(ctx, series.TMDBID)
	if err != nil {
		return nil, err
	}
	episodes := []library.Episode{}
	for _, seasonNumber := range newestSeasonNumbers(seasons) {
		seasonEpisodes, err := w.library.EnsureSeason(ctx, series.TMDBID, seasonNumber)
		if err != nil {
			return nil, err
		}
		episodes = append(episodes, seasonEpisodes...)
	}
	return SeriesNotices(series, episodes, today), nil
}

// newestSeasonNumbers returns the last seasonsChecked regular seasons, oldest first.
// seasons come ordered with specials last.
func newestSeasonNumbers(seasons []library.Season) []int {
	numbers := []int{}
	for _, season := range seasons {
		if season.SeasonNumber > 0 {
			numbers = append(numbers, season.SeasonNumber)
		}
	}
	if len(numbers) > seasonsChecked {
		numbers = numbers[len(numbers)-seasonsChecked:]
	}
	return numbers
}

func (w *Watcher) deliver(ctx context.Context, notices []Notice, followers []follower) {
	for _, notice := range notices {
		for _, profile := range followers {
			if !notice.AppliesTo(profile.AddedAt) {
				continue
			}
			w.addFor(ctx, notice, profile.ProfileID)
		}
	}
}

func (w *Watcher) addFor(ctx context.Context, notice Notice, profileID int) {
	_, err := w.store.Add(ctx, Notification{
		ProfileID: profileID,
		Kind:      notice.Kind,
		MediaType: notice.Ref.MediaType,
		MediaID:   notice.Ref.MediaID,
		EpisodeID: notice.EpisodeID,
		Heading:   notice.Heading,
		Body:      notice.Body,
	})
	if err != nil {
		log.Printf("notify: store for profile %d: %v", profileID, err)
	}
}
