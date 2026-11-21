package profiles

import (
	"context"
	"errors"
	"fmt"
	"strings"

	"github.com/Letsmoe/otter/internal/db"
	"github.com/Letsmoe/otter/internal/library"
)

// maxBookmarkCaption keeps a bookmark's line short; a caption cue never needs more.
const maxBookmarkCaption = 300

// Bookmark is a moment of a movie or episode a profile saved while watching.
type Bookmark struct {
	ID              int               `json:"id"`
	ProfileID       int               `json:"profile_id"`
	MediaType       library.MediaType `json:"media_type"`
	MediaID         int               `json:"media_id"`
	PositionSeconds float64           `json:"position_seconds"`
	Caption         string            `json:"caption"`
	CreatedAt       string            `json:"created_at"`
}

func (b Bookmark) Ref() library.MediaRef {
	return library.MediaRef{MediaType: b.MediaType, MediaID: b.MediaID}
}

// AddBookmark saves the position (and the caption line spoken then, if any) of a movie or episode.
func (s *Service) AddBookmark(ctx context.Context, profileID int, ref library.MediaRef, positionSeconds float64, caption string) (*Bookmark, error) {
	if ref.MediaType != library.MediaTypeMovie && ref.MediaType != library.MediaTypeEpisode {
		return nil, errors.New("only a movie or an episode can be bookmarked")
	}
	if positionSeconds < 0 {
		return nil, errors.New("the position can't be negative")
	}
	vars := refVars(profileID, ref)
	vars["position"] = positionSeconds
	vars["caption"] = trimCaption(caption)
	row, err := s.database.QueryRow(ctx, `INSERT INTO bookmark (profile_id, media_type, media_id,
		position_seconds, caption, created_at)
	VALUES ($profile_id, $media_type, $media_id, $position, $caption, $now) RETURNING id`, vars)
	if err != nil {
		return nil, fmt.Errorf("add bookmark: %w", err)
	}
	return db.QueryOneAs[Bookmark](ctx, s.database, "SELECT * FROM bookmark WHERE id = $id",
		db.Vars{"id": db.AsInt(row["id"])})
}

func trimCaption(caption string) string {
	caption = strings.TrimSpace(caption)
	runes := []rune(caption)
	if len(runes) > maxBookmarkCaption {
		return string(runes[:maxBookmarkCaption])
	}
	return caption
}

// RemoveBookmark deletes one of the profile's bookmarks; other profiles' are left alone.
func (s *Service) RemoveBookmark(ctx context.Context, profileID, bookmarkID int) error {
	_, err := s.database.Exec(ctx, "DELETE FROM bookmark WHERE id = $id AND profile_id = $profile_id",
		db.Vars{"id": bookmarkID, "profile_id": profileID})
	return err
}

// Bookmarks lists the profile's bookmarks of a movie, or of every episode of a series
// (in watching order), earliest moment first.
func (s *Service) Bookmarks(ctx context.Context, profileID int, ref library.MediaRef) ([]Bookmark, error) {
	if ref.MediaType == library.MediaTypeSeries {
		return db.QueryAs[Bookmark](ctx, s.database, `SELECT bookmark.* FROM bookmark
			JOIN episode ON bookmark.media_type = 'episode' AND episode.tmdb_id = bookmark.media_id
			WHERE bookmark.profile_id = $profile_id AND episode.series_id = $media_id
			ORDER BY episode.season_number, episode.episode_number, bookmark.position_seconds`,
			db.Vars{"profile_id": profileID, "media_id": ref.MediaID})
	}
	return db.QueryAs[Bookmark](ctx, s.database, `SELECT * FROM bookmark
		WHERE profile_id = $profile_id AND media_type = $media_type AND media_id = $media_id
		ORDER BY position_seconds`, refVars(profileID, ref))
}
