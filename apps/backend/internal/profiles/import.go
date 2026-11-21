package profiles

import (
	"context"

	"github.com/Letsmoe/otter/internal/library"
)

// The Import methods take what happened elsewhere (a Letterboxd diary) and
// record it with its own dates. They emit no hooks, and never undo what
// Otter itself recorded: each returns whether it changed anything.

// ImportWatched marks a title as watched from startedAt to watchedAt. A
// finished title stays as it is; a started one becomes finished; one never
// played gets an entry without a real position (the length is unknown).
func (s *Service) ImportWatched(ctx context.Context, profileID int, ref library.MediaRef, startedAt, watchedAt string) (bool, error) {
	vars := refVars(profileID, ref)
	vars["started_at"] = startedAt
	vars["watched_at"] = watchedAt
	changed, err := s.database.Exec(ctx, `INSERT INTO watch_history (profile_id, media_type, media_id,
		progress_seconds, duration_seconds, completed, started_at, updated_at)
	VALUES ($profile_id, $media_type, $media_id, 1, 1, 1, $started_at, $watched_at)
	ON CONFLICT (profile_id, media_type, media_id) DO UPDATE SET completed = 1
		WHERE watch_history.completed = 0`, vars)
	return changed > 0, err
}

// ImportRating sets a 1-10 rating given on ratedAt, unless the title was
// rated later (in Otter or by an earlier import).
func (s *Service) ImportRating(ctx context.Context, profileID int, ref library.MediaRef, rating float64, ratedAt string) (bool, error) {
	vars := refVars(profileID, ref)
	vars["rating"] = rating
	vars["rated_at"] = ratedAt
	changed, err := s.database.Exec(ctx, `INSERT INTO user_rating (profile_id, media_type, media_id, rating, rated_at)
	VALUES ($profile_id, $media_type, $media_id, $rating, $rated_at)
	ON CONFLICT (profile_id, media_type, media_id) DO UPDATE SET
		rating = excluded.rating, rated_at = excluded.rated_at
		WHERE excluded.rated_at > user_rating.rated_at`, vars)
	return changed > 0, err
}

// ImportWatchlist saves a title to My List as of addedAt; a title already on
// the list keeps its date.
func (s *Service) ImportWatchlist(ctx context.Context, profileID int, ref library.MediaRef, addedAt string) (bool, error) {
	vars := refVars(profileID, ref)
	vars["added_at"] = addedAt
	changed, err := s.database.Exec(ctx, `INSERT OR IGNORE INTO watchlist (profile_id, media_type, media_id, added_at)
		VALUES ($profile_id, $media_type, $media_id, $added_at)`, vars)
	if err != nil || changed == 0 {
		return false, err
	}
	return true, s.clearNotifyingChoice(ctx, profileID, ref)
}
