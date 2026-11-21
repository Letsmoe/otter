package profiles

import (
	"context"
	"errors"
	"time"

	"github.com/Letsmoe/otter/internal/db"
	"github.com/Letsmoe/otter/internal/library"
)

// ErrNotRewatchable is returned for anything but a movie or a series.
var ErrNotRewatchable = errors.New("only a movie or a series can be rewatched")

// Rewatch is one run through a movie or series with progress of its own.
// While it is active, the title's progress is read from and written to
// rewatch_progress, so Continue watching, history and recommendations keep
// what the first watch recorded.
type Rewatch struct {
	ProfileID  int               `json:"profile_id"`
	MediaType  library.MediaType `json:"media_type"`
	MediaID    int               `json:"media_id"`
	StartedAt  string            `json:"started_at"`
	UpdatedAt  string            `json:"updated_at"`
	FinishedAt string            `json:"finished_at"`
}

// Active reports whether the run is still going.
func (r Rewatch) Active() bool {
	return r.FinishedAt == ""
}

// Title is the movie or series the run belongs to.
func (r Rewatch) Title() library.MediaRef {
	return library.MediaRef{MediaType: r.MediaType, MediaID: r.MediaID}
}

// StartRewatch begins a fresh run through a movie or series, dropping the
// progress of an earlier one. The first watch's progress is not touched.
func (s *Service) StartRewatch(ctx context.Context, profileID int, title library.MediaRef) (*Rewatch, error) {
	if title.MediaType != library.MediaTypeMovie && title.MediaType != library.MediaTypeSeries {
		return nil, ErrNotRewatchable
	}
	err := s.database.Tx(ctx, func(tx *db.DB) error {
		if err := deleteRewatchProgress(ctx, tx, profileID, title); err != nil {
			return err
		}
		_, err := tx.Exec(ctx, `INSERT INTO rewatch (profile_id, media_type, media_id, started_at, updated_at, finished_at)
			VALUES ($profile_id, $media_type, $media_id, $now, $now, '')
			ON CONFLICT (profile_id, media_type, media_id) DO UPDATE SET
				started_at = excluded.started_at, updated_at = excluded.updated_at, finished_at = ''`,
			refVars(profileID, title))
		return err
	})
	if err != nil {
		return nil, err
	}
	return s.Rewatch(ctx, profileID, title)
}

// StopRewatch ends the run and forgets its progress.
func (s *Service) StopRewatch(ctx context.Context, profileID int, title library.MediaRef) error {
	return s.database.Tx(ctx, func(tx *db.DB) error {
		if err := deleteRewatchProgress(ctx, tx, profileID, title); err != nil {
			return err
		}
		_, err := tx.Exec(ctx, `DELETE FROM rewatch WHERE profile_id = $profile_id
			AND media_type = $media_type AND media_id = $media_id`, refVars(profileID, title))
		return err
	})
}

func deleteRewatchProgress(ctx context.Context, tx *db.DB, profileID int, title library.MediaRef) error {
	vars := refVars(profileID, title)
	if title.MediaType == library.MediaTypeMovie {
		_, err := tx.Exec(ctx, `DELETE FROM rewatch_progress WHERE profile_id = $profile_id
			AND media_type = $media_type AND media_id = $media_id`, vars)
		return err
	}
	_, err := tx.Exec(ctx, `DELETE FROM rewatch_progress WHERE profile_id = $profile_id AND media_type = 'episode'
		AND media_id IN (SELECT tmdb_id FROM episode WHERE series_id = $media_id)`, vars)
	return err
}

// Rewatch returns the profile's latest run through a title, finished or not;
// nil when there never was one.
func (s *Service) Rewatch(ctx context.Context, profileID int, title library.MediaRef) (*Rewatch, error) {
	return db.QueryOneAs[Rewatch](ctx, s.database, `SELECT * FROM rewatch WHERE profile_id = $profile_id
		AND media_type = $media_type AND media_id = $media_id`, refVars(profileID, title))
}

// ActiveRewatches lists the runs still going, the most recently watched first.
func (s *Service) ActiveRewatches(ctx context.Context, profileID, limit int) ([]Rewatch, error) {
	return db.QueryAs[Rewatch](ctx, s.database, `SELECT * FROM rewatch WHERE profile_id = $profile_id
		AND finished_at = '' ORDER BY updated_at DESC LIMIT $limit`,
		db.Vars{"profile_id": profileID, "limit": limit})
}

// LatestRewatchEpisode is the episode of a series' active run watched last;
// nil when no episode was started yet.
func (s *Service) LatestRewatchEpisode(ctx context.Context, profileID, seriesID int) (*WatchEntry, error) {
	entry, err := db.QueryOneAs[WatchEntry](ctx, s.database, `SELECT rewatch_progress.* FROM rewatch_progress
		JOIN episode ON episode.tmdb_id = rewatch_progress.media_id
		WHERE rewatch_progress.profile_id = $profile_id AND rewatch_progress.media_type = 'episode'
		AND episode.series_id = $series_id ORDER BY rewatch_progress.updated_at DESC LIMIT 1`,
		db.Vars{"profile_id": profileID, "series_id": seriesID})
	if entry != nil {
		entry.Rewatch = true
	}
	return entry, err
}

// activeRewatchFor finds the running rewatch a movie or episode belongs to;
// nil when its title is not being rewatched.
func (s *Service) activeRewatchFor(ctx context.Context, profileID int, ref library.MediaRef) (*Rewatch, error) {
	title, err := s.titleOf(ctx, ref)
	if err != nil {
		return nil, err
	}
	if title.MediaType != library.MediaTypeMovie && title.MediaType != library.MediaTypeSeries {
		return nil, nil
	}
	rewatch, err := s.Rewatch(ctx, profileID, title)
	if err != nil || rewatch == nil || !rewatch.Active() {
		return nil, err
	}
	return rewatch, nil
}

// titleOf maps an episode to its series; other refs are their own title.
func (s *Service) titleOf(ctx context.Context, ref library.MediaRef) (library.MediaRef, error) {
	if ref.MediaType != library.MediaTypeEpisode {
		return ref, nil
	}
	row, err := s.database.QueryRow(ctx, `SELECT series_id FROM episode WHERE tmdb_id = $media_id`,
		db.Vars{"media_id": ref.MediaID})
	if err != nil || row == nil {
		return ref, err
	}
	return library.MediaRef{MediaType: library.MediaTypeSeries, MediaID: db.AsInt(row["series_id"])}, nil
}

func (s *Service) rewatchEntry(ctx context.Context, profileID int, ref library.MediaRef) (*WatchEntry, error) {
	entry, err := db.QueryOneAs[WatchEntry](ctx, s.database, `SELECT * FROM rewatch_progress
		WHERE profile_id = $profile_id AND media_type = $media_type AND media_id = $media_id`,
		refVars(profileID, ref))
	if entry != nil {
		entry.Rewatch = true
	}
	return entry, err
}

// recordRewatchProgress stores progress of the run; it emits no hooks and
// leaves watch_history alone. The run finishes with its last title.
func (s *Service) recordRewatchProgress(ctx context.Context, rewatch *Rewatch, ref library.MediaRef, progressSeconds, durationSeconds float64) (*WatchEntry, error) {
	vars := refVars(rewatch.ProfileID, ref)
	vars["progress"] = progressSeconds
	vars["duration"] = durationSeconds
	vars["completed"] = isCompleted(progressSeconds, durationSeconds)
	_, err := s.database.Exec(ctx, `INSERT INTO rewatch_progress (profile_id, media_type, media_id,
		progress_seconds, duration_seconds, completed, started_at, updated_at)
	VALUES ($profile_id, $media_type, $media_id, $progress, $duration, $completed, $now, $now)
	ON CONFLICT (profile_id, media_type, media_id) DO UPDATE SET
		progress_seconds = excluded.progress_seconds, duration_seconds = excluded.duration_seconds,
		completed = rewatch_progress.completed OR excluded.completed, updated_at = excluded.updated_at`, vars)
	if err != nil {
		return nil, err
	}
	entry, err := s.rewatchEntry(ctx, rewatch.ProfileID, ref)
	if err != nil || entry == nil {
		return entry, err
	}
	return entry, s.updateRewatchRun(ctx, rewatch, entry)
}

// updateRewatchRun marks the run as most recently watched, and finished once
// everything in it was watched again.
func (s *Service) updateRewatchRun(ctx context.Context, rewatch *Rewatch, entry *WatchEntry) error {
	finishedAt := ""
	if entry.Completed {
		finished, err := s.rewatchIsComplete(ctx, rewatch)
		if err != nil {
			return err
		}
		if finished {
			finishedAt = db.Now()
		}
	}
	vars := refVars(rewatch.ProfileID, rewatch.Title())
	vars["finished_at"] = finishedAt
	_, err := s.database.Exec(ctx, `UPDATE rewatch SET updated_at = $now, finished_at = $finished_at
		WHERE profile_id = $profile_id AND media_type = $media_type AND media_id = $media_id`, vars)
	return err
}

func (s *Service) rewatchIsComplete(ctx context.Context, rewatch *Rewatch) (bool, error) {
	if rewatch.MediaType == library.MediaTypeMovie {
		return true, nil
	}
	vars := db.Vars{"profile_id": rewatch.ProfileID, "series_id": rewatch.MediaID, "today": time.Now().Format("2006-01-02")}
	aired := `SELECT tmdb_id FROM episode WHERE series_id = $series_id AND season_number > 0
		AND air_date IS NOT NULL AND air_date != '' AND air_date <= $today`
	total, err := s.database.Int(ctx, "SELECT COUNT(*) FROM ("+aired+")", vars)
	if err != nil || total == 0 {
		return false, err
	}
	missing, err := s.database.Int(ctx, "SELECT COUNT(*) FROM ("+aired+`) WHERE tmdb_id NOT IN (
		SELECT media_id FROM rewatch_progress WHERE profile_id = $profile_id
		AND media_type = 'episode' AND completed)`, vars)
	return missing == 0, err
}
