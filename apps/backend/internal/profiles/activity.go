package profiles

import (
	"context"

	"github.com/Letsmoe/otter/internal/db"
	"github.com/Letsmoe/otter/internal/hooks"
	"github.com/Letsmoe/otter/internal/library"
)

// completedFraction is how much of a title must be watched to count as seen;
// the rest is usually credits.
const completedFraction = 0.9

type WatchEntry struct {
	ProfileID       int               `json:"profile_id"`
	MediaType       library.MediaType `json:"media_type"`
	MediaID         int               `json:"media_id"`
	ProgressSeconds float64           `json:"progress_seconds"`
	DurationSeconds float64           `json:"duration_seconds"`
	Completed       bool              `json:"completed"`
	StartedAt       string            `json:"started_at"`
	UpdatedAt       string            `json:"updated_at"`
	// Rewatch marks progress of a rewatch run, not of the first watch.
	Rewatch bool `json:"rewatch"`
}

func (e WatchEntry) Ref() library.MediaRef {
	return library.MediaRef{MediaType: e.MediaType, MediaID: e.MediaID}
}

type Rating struct {
	MediaType library.MediaType `json:"media_type"`
	MediaID   int               `json:"media_id"`
	Rating    float64           `json:"rating"`
	RatedAt   string            `json:"rated_at"`
}

type WatchlistEntry struct {
	MediaType library.MediaType `json:"media_type"`
	MediaID   int               `json:"media_id"`
	AddedAt   string            `json:"added_at"`
}

func refVars(profileID int, ref library.MediaRef) db.Vars {
	return db.Vars{"profile_id": profileID, "media_type": ref.MediaType, "media_id": ref.MediaID, "now": db.Now()}
}

// RecordProgress stores playback progress for a movie or episode and emits
// watch.started / watch.completed when those happen for the first time.
// During a rewatch of the title the progress goes to the rewatch instead and
// no events fire.
func (s *Service) RecordProgress(ctx context.Context, profileID int, ref library.MediaRef, progressSeconds, durationSeconds float64) (*WatchEntry, error) {
	rewatch, err := s.activeRewatchFor(ctx, profileID, ref)
	if err != nil {
		return nil, err
	}
	if rewatch != nil {
		return s.recordRewatchProgress(ctx, rewatch, ref, progressSeconds, durationSeconds)
	}
	return s.recordFirstWatchProgress(ctx, profileID, ref, progressSeconds, durationSeconds)
}

func (s *Service) recordFirstWatchProgress(ctx context.Context, profileID int, ref library.MediaRef, progressSeconds, durationSeconds float64) (*WatchEntry, error) {
	previous, err := s.historyEntry(ctx, profileID, ref)
	if err != nil {
		return nil, err
	}
	vars := refVars(profileID, ref)
	vars["progress"] = progressSeconds
	vars["duration"] = durationSeconds
	vars["completed"] = isCompleted(progressSeconds, durationSeconds)
	_, err = s.database.Exec(ctx, `INSERT INTO watch_history (profile_id, media_type, media_id,
		progress_seconds, duration_seconds, completed, started_at, updated_at)
	VALUES ($profile_id, $media_type, $media_id, $progress, $duration, $completed, $now, $now)
	ON CONFLICT (profile_id, media_type, media_id) DO UPDATE SET
		progress_seconds = excluded.progress_seconds, duration_seconds = excluded.duration_seconds,
		completed = watch_history.completed OR excluded.completed, updated_at = excluded.updated_at`, vars)
	if err != nil {
		return nil, err
	}
	current, err := s.historyEntry(ctx, profileID, ref)
	if err != nil {
		return nil, err
	}
	s.emitWatchEvents(previous, current)
	return current, nil
}

func isCompleted(progressSeconds, durationSeconds float64) bool {
	return durationSeconds > 0 && progressSeconds/durationSeconds >= completedFraction
}

// emitWatchEvents fires hooks for the first start and the first completion.
// Payload: the watch entry.
func (s *Service) emitWatchEvents(previous, current *WatchEntry) {
	if previous == nil {
		s.hooks.Emit(hooks.EventWatchStarted, current)
	}
	wasCompleted := previous != nil && previous.Completed
	if current.Completed && !wasCompleted {
		s.hooks.Emit(hooks.EventWatchCompleted, current)
	}
}

// WatchEntry returns the profile's progress on one title; nil when unwatched.
// During a rewatch of the title it is the rewatch's progress.
func (s *Service) WatchEntry(ctx context.Context, profileID int, ref library.MediaRef) (*WatchEntry, error) {
	rewatch, err := s.activeRewatchFor(ctx, profileID, ref)
	if err != nil {
		return nil, err
	}
	if rewatch != nil {
		return s.rewatchEntry(ctx, profileID, ref)
	}
	return s.historyEntry(ctx, profileID, ref)
}

// historyEntry is the progress of the first watch, whatever a rewatch says.
func (s *Service) historyEntry(ctx context.Context, profileID int, ref library.MediaRef) (*WatchEntry, error) {
	return db.QueryOneAs[WatchEntry](ctx, s.database, `SELECT * FROM watch_history
		WHERE profile_id = $profile_id AND media_type = $media_type AND media_id = $media_id`,
		refVars(profileID, ref))
}

// History lists the profile's watched titles, most recent first.
func (s *Service) History(ctx context.Context, profileID, limit int) ([]WatchEntry, error) {
	return db.QueryAs[WatchEntry](ctx, s.database, `SELECT * FROM watch_history
		WHERE profile_id = $profile_id ORDER BY updated_at DESC LIMIT $limit`,
		db.Vars{"profile_id": profileID, "limit": limit})
}

// EpisodeProgress lists the profile's progress on a series' episodes; during
// a rewatch of the series it is the rewatch's progress.
func (s *Service) EpisodeProgress(ctx context.Context, profileID, seriesID int) ([]WatchEntry, error) {
	table := "watch_history"
	series := library.MediaRef{MediaType: library.MediaTypeSeries, MediaID: seriesID}
	rewatch, err := s.Rewatch(ctx, profileID, series)
	if err != nil {
		return nil, err
	}
	if rewatch != nil && rewatch.Active() {
		table = "rewatch_progress"
	}
	entries, err := db.QueryAs[WatchEntry](ctx, s.database, `SELECT progress.* FROM `+table+` AS progress
		JOIN episode ON episode.tmdb_id = progress.media_id
		WHERE progress.profile_id = $profile_id AND progress.media_type = 'episode'
		AND episode.series_id = $series_id`,
		db.Vars{"profile_id": profileID, "series_id": seriesID})
	for index := range entries {
		entries[index].Rewatch = table == "rewatch_progress"
	}
	return entries, err
}

// SetRating stores a 1-10 rating and emits rating.set (payload: the rating
// plus the profile id).
func (s *Service) SetRating(ctx context.Context, profileID int, ref library.MediaRef, rating float64) error {
	vars := refVars(profileID, ref)
	vars["rating"] = rating
	_, err := s.database.Exec(ctx, `INSERT INTO user_rating (profile_id, media_type, media_id, rating, rated_at)
		VALUES ($profile_id, $media_type, $media_id, $rating, $now)
		ON CONFLICT (profile_id, media_type, media_id) DO UPDATE SET
			rating = excluded.rating, rated_at = excluded.rated_at`, vars)
	if err != nil {
		return err
	}
	s.hooks.Emit(hooks.EventRatingSet, map[string]any{
		"profile_id": profileID, "media_type": ref.MediaType, "media_id": ref.MediaID, "rating": rating,
	})
	return nil
}

func (s *Service) ClearRating(ctx context.Context, profileID int, ref library.MediaRef) error {
	_, err := s.database.Exec(ctx, `DELETE FROM user_rating WHERE profile_id = $profile_id
		AND media_type = $media_type AND media_id = $media_id`, refVars(profileID, ref))
	return err
}

// Rating returns the profile's rating of a title; nil when unrated.
func (s *Service) Rating(ctx context.Context, profileID int, ref library.MediaRef) (*Rating, error) {
	return db.QueryOneAs[Rating](ctx, s.database, `SELECT media_type, media_id, rating, rated_at
		FROM user_rating WHERE profile_id = $profile_id AND media_type = $media_type
		AND media_id = $media_id`, refVars(profileID, ref))
}

// Ratings lists every rating of a profile.
func (s *Service) Ratings(ctx context.Context, profileID int) ([]Rating, error) {
	return db.QueryAs[Rating](ctx, s.database, `SELECT media_type, media_id, rating, rated_at
		FROM user_rating WHERE profile_id = $profile_id`, db.Vars{"profile_id": profileID})
}

// AddToWatchlist saves a title and emits watchlist.added (payload: the
// entry plus the profile id).
func (s *Service) AddToWatchlist(ctx context.Context, profileID int, ref library.MediaRef) error {
	_, err := s.database.Exec(ctx, `INSERT OR IGNORE INTO watchlist (profile_id, media_type, media_id, added_at)
		VALUES ($profile_id, $media_type, $media_id, $now)`, refVars(profileID, ref))
	if err != nil {
		return err
	}
	if err := s.clearNotifyingChoice(ctx, profileID, ref); err != nil {
		return err
	}
	s.hooks.Emit(hooks.EventWatchlistAdded, map[string]any{
		"profile_id": profileID, "media_type": ref.MediaType, "media_id": ref.MediaID,
	})
	return nil
}

func (s *Service) RemoveFromWatchlist(ctx context.Context, profileID int, ref library.MediaRef) error {
	_, err := s.database.Exec(ctx, `DELETE FROM watchlist WHERE profile_id = $profile_id
		AND media_type = $media_type AND media_id = $media_id`, refVars(profileID, ref))
	if err != nil {
		return err
	}
	return s.clearNotifyingChoice(ctx, profileID, ref)
}

// Watchlist lists the profile's saved titles, newest first.
func (s *Service) Watchlist(ctx context.Context, profileID int) ([]WatchlistEntry, error) {
	return db.QueryAs[WatchlistEntry](ctx, s.database, `SELECT media_type, media_id, added_at
		FROM watchlist WHERE profile_id = $profile_id ORDER BY added_at DESC`,
		db.Vars{"profile_id": profileID})
}

// InWatchlist reports whether the profile saved a title.
func (s *Service) InWatchlist(ctx context.Context, profileID int, ref library.MediaRef) (bool, error) {
	count, err := s.database.Int(ctx, `SELECT COUNT(*) FROM watchlist WHERE profile_id = $profile_id
		AND media_type = $media_type AND media_id = $media_id`, refVars(profileID, ref))
	return count > 0, err
}
