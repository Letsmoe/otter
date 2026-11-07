package profiles

import (
	"context"

	"github.com/Letsmoe/otter/internal/db"
	"github.com/Letsmoe/otter/internal/library"
)

// SetNotifying turns news about a title on or off for a profile (the "Notify
// me" bell), whether or not the title is on My List.
func (s *Service) SetNotifying(ctx context.Context, profileID int, ref library.MediaRef, notifying bool) error {
	vars := refVars(profileID, ref)
	vars["following"] = notifying
	_, err := s.database.Exec(ctx, `INSERT INTO notification_follow
		(profile_id, media_type, media_id, following, followed_at)
		VALUES ($profile_id, $media_type, $media_id, $following, $now)
		ON CONFLICT (profile_id, media_type, media_id)
		DO UPDATE SET following = excluded.following, followed_at = excluded.followed_at`, vars)
	return err
}

// Notifying reports whether a profile gets news about a title: the bell's
// choice when there is one, else whether the title is on My List.
func (s *Service) Notifying(ctx context.Context, profileID int, ref library.MediaRef) (bool, error) {
	choice, err := s.database.QueryRow(ctx, `SELECT following FROM notification_follow
		WHERE profile_id = $profile_id AND media_type = $media_type AND media_id = $media_id`,
		refVars(profileID, ref))
	if err != nil {
		return false, err
	}
	if choice != nil {
		return db.AsInt(choice["following"]) == 1, nil
	}
	return s.InWatchlist(ctx, profileID, ref)
}

// clearNotifyingChoice drops the bell's choice, so the title notifies exactly
// when it is on My List again.
func (s *Service) clearNotifyingChoice(ctx context.Context, profileID int, ref library.MediaRef) error {
	_, err := s.database.Exec(ctx, `DELETE FROM notification_follow WHERE profile_id = $profile_id
		AND media_type = $media_type AND media_id = $media_id`, refVars(profileID, ref))
	return err
}
