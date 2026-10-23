// Package notify keeps per-profile notifications about titles on a profile's
// watchlist: a movie got its first stream links, a followed series aired a new
// episode, or a release is coming up. The Watcher produces them.
package notify

import (
	"context"

	"github.com/neoworks/otter/internal/db"
	"github.com/neoworks/otter/internal/library"
)

type Kind string

const (
	KindAvailable  Kind = "available"
	KindNewEpisode Kind = "new_episode"
	KindComingSoon Kind = "coming_soon"
)

type Notification struct {
	ID        int               `json:"id"`
	ProfileID int               `json:"profile_id"`
	Kind      Kind              `json:"kind"`
	MediaType library.MediaType `json:"media_type"`
	MediaID   int               `json:"media_id"`
	// EpisodeID is 0 unless the notification is about one episode.
	EpisodeID int    `json:"episode_id"`
	Heading   string `json:"heading"`
	Body      string `json:"body"`
	CreatedAt string `json:"created_at"`
	ReadAt    string `json:"read_at"`
}

// Ref is the movie or series the notification is about.
func (n Notification) Ref() library.MediaRef {
	return library.MediaRef{MediaType: n.MediaType, MediaID: n.MediaID}
}

type Store struct {
	database *db.DB
}

func NewStore(database *db.DB) *Store {
	return &Store{database: database}
}

// Add stores a notification unless the profile already got the same kind for
// the same title (and episode). Reports whether it was new.
func (s *Store) Add(ctx context.Context, notification Notification) (bool, error) {
	added, err := s.database.Exec(ctx, `INSERT OR IGNORE INTO notification
		(profile_id, kind, media_type, media_id, episode_id, heading, body, created_at)
		VALUES ($profile_id, $kind, $media_type, $media_id, $episode_id, $heading, $body, $now)`,
		db.Vars{
			"profile_id": notification.ProfileID,
			"kind":       notification.Kind,
			"media_type": notification.MediaType,
			"media_id":   notification.MediaID,
			"episode_id": notification.EpisodeID,
			"heading":    notification.Heading,
			"body":       notification.Body,
			"now":        db.Now(),
		})
	return added > 0, err
}

// List returns the profile's notifications, newest first.
func (s *Store) List(ctx context.Context, profileID, limit int) ([]Notification, error) {
	return db.QueryAs[Notification](ctx, s.database, `SELECT * FROM notification
		WHERE profile_id = $profile_id ORDER BY created_at DESC, id DESC LIMIT $limit`,
		db.Vars{"profile_id": profileID, "limit": limit})
}

func (s *Store) UnreadCount(ctx context.Context, profileID int) (int, error) {
	return s.database.Int(ctx, `SELECT COUNT(*) FROM notification
		WHERE profile_id = $profile_id AND read_at IS NULL`, db.Vars{"profile_id": profileID})
}

// MarkAllRead marks every unread notification of the profile as read.
func (s *Store) MarkAllRead(ctx context.Context, profileID int) error {
	_, err := s.database.Exec(ctx, `UPDATE notification SET read_at = $now
		WHERE profile_id = $profile_id AND read_at IS NULL`,
		db.Vars{"profile_id": profileID, "now": db.Now()})
	return err
}
