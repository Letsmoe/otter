package letterboxd

import (
	"context"
	"errors"
	"log"
	"time"

	"github.com/Letsmoe/otter/internal/db"
)

const (
	// The feed lists the latest few dozen entries, so a few checks a day lose nothing.
	syncInterval = 6 * time.Hour
	// Waiting a little after the start keeps the first sync out of the app's busy start.
	firstSyncDelay = time.Minute
	syncTimeout    = 5 * time.Minute
)

// Connection is the Letterboxd account whose diary feed a profile follows.
type Connection struct {
	ProfileID    int    `json:"profile_id"`
	Username     string `json:"username"`
	LastSyncedAt string `json:"last_synced_at"`
	// LastError is why the last sync failed; empty after a good one.
	LastError   string `json:"last_error"`
	LastWatched int    `json:"last_watched"`
	LastRated   int    `json:"last_rated"`
}

// ErrNotConnected is returned when a profile follows no Letterboxd account.
var ErrNotConnected = errors.New("this profile follows no Letterboxd account")

// ConnectionOf returns the account the profile follows; nil when none.
func (s *Service) ConnectionOf(ctx context.Context, profileID int) (*Connection, error) {
	return db.QueryOneAs[Connection](ctx, s.database, "SELECT * FROM letterboxd_sync WHERE profile_id = $profile_id",
		db.Vars{"profile_id": profileID})
}

// Connect makes the profile follow a Letterboxd account's public diary. A
// different username starts over, so the last result isn't shown for it.
func (s *Service) Connect(ctx context.Context, profileID int, input string) (*Connection, error) {
	username, err := NormalizeUsername(input)
	if err != nil {
		return nil, err
	}
	_, err = s.database.Exec(ctx, `INSERT INTO letterboxd_sync (profile_id, username) VALUES ($profile_id, $username)
		ON CONFLICT (profile_id) DO UPDATE SET username = excluded.username,
			last_synced_at = '', last_error = '', last_watched = 0, last_rated = 0
			WHERE letterboxd_sync.username != excluded.username`,
		db.Vars{"profile_id": profileID, "username": username})
	if err != nil {
		return nil, err
	}
	return s.ConnectionOf(ctx, profileID)
}

// Disconnect stops following the account; what was imported stays.
func (s *Service) Disconnect(ctx context.Context, profileID int) error {
	_, err := s.database.Exec(ctx, "DELETE FROM letterboxd_sync WHERE profile_id = $profile_id",
		db.Vars{"profile_id": profileID})
	return err
}

// SyncNow reads the followed account's diary feed and records its new
// watches and ratings. A failed read is kept in the connection's LastError;
// only a profile without an account is an error.
func (s *Service) SyncNow(ctx context.Context, profileID int) (*Connection, error) {
	connection, err := s.ConnectionOf(ctx, profileID)
	if err != nil {
		return nil, err
	}
	if connection == nil {
		return nil, ErrNotConnected
	}
	watched, rated, syncErr := s.syncFeed(ctx, profileID, connection.Username)
	if err := s.saveSyncResult(ctx, profileID, watched, rated, syncErr); err != nil {
		return nil, err
	}
	if watched+rated > 0 {
		s.changed()
	}
	return s.ConnectionOf(ctx, profileID)
}

func (s *Service) syncFeed(ctx context.Context, profileID int, username string) (watched, rated int, err error) {
	items, err := FetchFeed(ctx, s.client, username)
	if err != nil {
		return 0, 0, err
	}
	for _, item := range items {
		newWatch, newRating, err := s.applyFeedItem(ctx, profileID, item)
		if err != nil {
			return watched, rated, err
		}
		watched += boolCount(newWatch)
		rated += boolCount(newRating)
	}
	return watched, rated, nil
}

func boolCount(value bool) int {
	if value {
		return 1
	}
	return 0
}

// applyFeedItem records one diary entry; both results are false for an
// entry Otter already had.
func (s *Service) applyFeedItem(ctx context.Context, profileID int, item FeedItem) (watched, rated bool, err error) {
	ref, err := s.catalog.EnsureMovie(ctx, item.TMDBID)
	if err != nil {
		return false, false, err
	}
	stamp := dateStamp(item.Date)
	watched, err = s.activity.ImportWatched(ctx, profileID, ref, stamp, stamp)
	if err != nil || item.Stars == 0 {
		return watched, false, err
	}
	rated, err = s.activity.ImportRating(ctx, profileID, ref, OtterRating(item.Stars), stamp)
	return watched, rated, err
}

func (s *Service) saveSyncResult(ctx context.Context, profileID, watched, rated int, syncErr error) error {
	message := ""
	if syncErr != nil {
		message = syncErr.Error()
	}
	_, err := s.database.Exec(ctx, `UPDATE letterboxd_sync SET last_synced_at = $now, last_error = $error,
		last_watched = $watched, last_rated = $rated WHERE profile_id = $profile_id`,
		db.Vars{"profile_id": profileID, "now": db.Now(), "error": message, "watched": watched, "rated": rated})
	return err
}

// Run follows every connected account's feed until ctx ends: shortly after
// the start, then every few hours.
func (s *Service) Run(ctx context.Context) {
	timer := time.NewTimer(firstSyncDelay)
	defer timer.Stop()
	for {
		select {
		case <-ctx.Done():
			return
		case <-timer.C:
			s.syncEveryone(ctx)
			timer.Reset(syncInterval)
		}
	}
}

func (s *Service) syncEveryone(ctx context.Context) {
	connections, err := db.QueryAs[Connection](ctx, s.database, "SELECT * FROM letterboxd_sync", nil)
	if err != nil {
		log.Printf("letterboxd: listing connections: %v", err)
		return
	}
	for _, connection := range connections {
		syncContext, cancel := context.WithTimeout(ctx, syncTimeout)
		if _, err := s.SyncNow(syncContext, connection.ProfileID); err != nil {
			log.Printf("letterboxd: syncing profile %d: %v", connection.ProfileID, err)
		}
		cancel()
	}
}
