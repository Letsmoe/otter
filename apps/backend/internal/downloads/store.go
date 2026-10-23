// Package downloads saves movies and episodes for offline playback: a queue
// worker picks the best stream link, resolves it and mirrors it into the
// stream cache, where download rows pin it against eviction.
package downloads

import (
	"context"

	"github.com/neoworks/otter/internal/db"
	"github.com/neoworks/otter/internal/library"
)

type Status string

const (
	StatusQueued      Status = "queued"
	StatusDownloading Status = "downloading"
	StatusDone        Status = "done"
	StatusFailed      Status = "failed"
)

type Download struct {
	ID        int               `json:"id"`
	ProfileID int               `json:"profile_id"`
	MediaType library.MediaType `json:"media_type"`
	MediaID   int               `json:"media_id"`
	// SeriesID is the episode's series; 0 for movies.
	SeriesID int `json:"series_id"`
	// StreamID is the link the user picked; 0 lets the worker choose.
	StreamID int `json:"stream_id"`
	// ActiveStreamID is the link being saved, tried first when resuming.
	ActiveStreamID int     `json:"active_stream_id"`
	Status         Status  `json:"status"`
	Progress       float64 `json:"progress"`
	SizeBytes      int64   `json:"size_bytes"`
	FileURL        string  `json:"file_url"`
	MimeType       string  `json:"mime_type"`
	Error          string  `json:"error"`
	CreatedAt      string  `json:"created_at"`
	FinishedAt     string  `json:"finished_at"`
}

func (d Download) Ref() library.MediaRef {
	return library.MediaRef{MediaType: d.MediaType, MediaID: d.MediaID}
}

type Store struct {
	database *db.DB
}

func NewStore(database *db.DB) *Store {
	return &Store{database: database}
}

// Queue adds a download, or re-queues a failed one with the newly picked
// link. Existing queued, running or finished downloads stay as they are.
func (s *Store) Queue(ctx context.Context, profileID int, ref library.MediaRef, seriesID, streamID int) (*Download, error) {
	_, err := s.database.Exec(ctx, `INSERT INTO download
		(profile_id, media_type, media_id, series_id, stream_id, status, created_at)
		VALUES ($profile_id, $media_type, $media_id, $series_id, $stream_id, $queued, $now)
		ON CONFLICT (profile_id, media_type, media_id) DO UPDATE
		SET status = $queued, error = '', progress = 0, stream_id = $stream_id WHERE status = $failed`,
		db.Vars{
			"profile_id": profileID, "media_type": ref.MediaType, "media_id": ref.MediaID,
			"series_id": seriesID, "stream_id": streamID, "queued": StatusQueued, "failed": StatusFailed,
			"now": db.Now(),
		})
	if err != nil {
		return nil, err
	}
	return db.QueryOneAs[Download](ctx, s.database, `SELECT * FROM download WHERE profile_id = $profile_id
		AND media_type = $media_type AND media_id = $media_id`,
		db.Vars{"profile_id": profileID, "media_type": ref.MediaType, "media_id": ref.MediaID})
}

func (s *Store) Get(ctx context.Context, id int) (*Download, error) {
	return db.QueryOneAs[Download](ctx, s.database, "SELECT * FROM download WHERE id = $id", db.Vars{"id": id})
}

// List returns the profile's downloads, newest first.
func (s *Store) List(ctx context.Context, profileID int) ([]Download, error) {
	return db.QueryAs[Download](ctx, s.database, `SELECT * FROM download
		WHERE profile_id = $profile_id ORDER BY created_at DESC, id DESC`, db.Vars{"profile_id": profileID})
}

// Unfinished returns queued and interrupted downloads, oldest first.
func (s *Store) Unfinished(ctx context.Context) ([]Download, error) {
	return db.QueryAs[Download](ctx, s.database, `SELECT * FROM download
		WHERE status IN ($queued, $downloading) ORDER BY id`,
		db.Vars{"queued": StatusQueued, "downloading": StatusDownloading})
}

// Finished returns every finished download.
func (s *Store) Finished(ctx context.Context) ([]Download, error) {
	return db.QueryAs[Download](ctx, s.database, `SELECT * FROM download
		WHERE status = $done AND file_url != '' ORDER BY id`, db.Vars{"done": StatusDone})
}

func (s *Store) Delete(ctx context.Context, id int) error {
	_, err := s.database.Exec(ctx, "DELETE FROM download WHERE id = $id", db.Vars{"id": id})
	return err
}

func (s *Store) MarkDownloading(ctx context.Context, id int) error {
	_, err := s.database.Exec(ctx, `UPDATE download SET status = $status, error = '' WHERE id = $id`,
		db.Vars{"id": id, "status": StatusDownloading})
	return err
}

func (s *Store) SetActiveStream(ctx context.Context, id, streamID int) error {
	_, err := s.database.Exec(ctx, `UPDATE download SET active_stream_id = $stream_id WHERE id = $id`,
		db.Vars{"id": id, "stream_id": streamID})
	return err
}

func (s *Store) SetProgress(ctx context.Context, id int, progress float64, sizeBytes int64) error {
	_, err := s.database.Exec(ctx, `UPDATE download SET progress = $progress, size_bytes = $size WHERE id = $id`,
		db.Vars{"id": id, "progress": progress, "size": sizeBytes})
	return err
}

func (s *Store) MarkDone(ctx context.Context, id int, fileURL, mimeType string) error {
	_, err := s.database.Exec(ctx, `UPDATE download SET status = $status, progress = 1,
		file_url = $file_url, mime_type = $mime_type, finished_at = $now WHERE id = $id`,
		db.Vars{"id": id, "status": StatusDone, "file_url": fileURL, "mime_type": mimeType, "now": db.Now()})
	return err
}

func (s *Store) MarkFailed(ctx context.Context, id int, message string) error {
	_, err := s.database.Exec(ctx, `UPDATE download SET status = $status, error = $error WHERE id = $id`,
		db.Vars{"id": id, "status": StatusFailed, "error": message})
	return err
}

// PinnedURLs lists the blob URLs of finished downloads, for cache eviction.
func (s *Store) PinnedURLs(ctx context.Context) ([]string, error) {
	return s.database.Strings(ctx, "SELECT DISTINCT file_url FROM download WHERE file_url != ''", nil)
}

// FileInUse reports whether another download row points at the same blob.
func (s *Store) FileInUse(ctx context.Context, fileURL string, exceptID int) (bool, error) {
	count, err := s.database.Int(ctx, "SELECT COUNT(*) FROM download WHERE file_url = $url AND id != $id",
		db.Vars{"url": fileURL, "id": exceptID})
	return count > 0, err
}
