package profiles

import (
	"context"

	"github.com/Letsmoe/otter/internal/db"
	"github.com/Letsmoe/otter/internal/library"
)

// DismissTitle hides a title from the profile's recommendations ("Not for
// me"). The recommendation engine reads the table directly.
func (s *Service) DismissTitle(ctx context.Context, profileID int, ref library.MediaRef) error {
	_, err := s.database.Exec(ctx, `INSERT INTO dismissed_title (profile_id, media_type, media_id, dismissed_at)
		VALUES ($profile_id, $media_type, $media_id, $now)
		ON CONFLICT (profile_id, media_type, media_id) DO UPDATE SET dismissed_at = excluded.dismissed_at`,
		refVars(profileID, ref))
	return err
}

// RestoreTitle undoes DismissTitle.
func (s *Service) RestoreTitle(ctx context.Context, profileID int, ref library.MediaRef) error {
	_, err := s.database.Exec(ctx, `DELETE FROM dismissed_title WHERE profile_id = $profile_id
		AND media_type = $media_type AND media_id = $media_id`, refVars(profileID, ref))
	return err
}

// DismissedTitles lists the profile's hidden titles, most recent first.
func (s *Service) DismissedTitles(ctx context.Context, profileID int) ([]library.MediaRef, error) {
	return db.QueryAs[library.MediaRef](ctx, s.database, `SELECT media_type, media_id FROM dismissed_title
		WHERE profile_id = $profile_id ORDER BY dismissed_at DESC`, db.Vars{"profile_id": profileID})
}
