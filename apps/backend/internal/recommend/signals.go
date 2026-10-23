package recommend

import (
	"context"
	"fmt"
	"time"

	"github.com/neoworks/otter/internal/db"
	"github.com/neoworks/otter/internal/library"
)

// signal is one weighted interaction of a profile with a title.
type signal struct {
	media  library.MediaRef
	weight float64
	at     time.Time
	// disliked marks a low rating, which also excludes the title.
	disliked bool
}

// titleOfMedia maps episodes onto their series so episode activity counts
// toward the show. Queries alias their activity table as "activity".
const titleOfMedia = `CASE activity.media_type WHEN 'episode' THEN 'series' ELSE activity.media_type END AS media_type,
	CASE activity.media_type WHEN 'episode' THEN episode.series_id ELSE activity.media_id END AS media_id`

const episodeJoin = `LEFT JOIN episode ON activity.media_type = 'episode' AND episode.tmdb_id = activity.media_id`

var ratingQuery = `SELECT ` + titleOfMedia + `, activity.rating, activity.rated_at AS at
	FROM user_rating AS activity ` + episodeJoin + `
	WHERE activity.profile_id = $profile AND activity.rated_at >= $since`

var watchQuery = `SELECT ` + titleOfMedia + `, activity.progress_seconds AS progress,
	activity.duration_seconds AS duration, activity.completed, activity.updated_at AS at
	FROM watch_history AS activity ` + episodeJoin + `
	WHERE activity.profile_id = $profile AND activity.updated_at >= $since`

const saveQuery = `SELECT media_type, media_id, added_at AS at FROM watchlist
	WHERE profile_id = $profile AND added_at >= $since`

type activityRow struct {
	MediaType library.MediaType `json:"media_type"`
	MediaID   int               `json:"media_id"`
	Rating    float64           `json:"rating"`
	Progress  float64           `json:"progress"`
	Duration  float64           `json:"duration"`
	Completed bool              `json:"completed"`
	At        string            `json:"at"`
}

func (row activityRow) ref() library.MediaRef {
	return library.MediaRef{MediaType: row.MediaType, MediaID: row.MediaID}
}

func (row activityRow) time() time.Time {
	parsed, _ := time.Parse(db.TimeFormat, row.At)
	return parsed
}

// collectSignals reads a profile's interactions inside the signal window.
func (e *Engine) collectSignals(ctx context.Context, profileID int, now time.Time) ([]signal, error) {
	vars := db.Vars{"profile": profileID, "since": db.FormatTime(now.Add(-signalWindow))}
	ratings, err := e.activity(ctx, ratingQuery, vars)
	if err != nil {
		return nil, err
	}
	watches, err := e.activity(ctx, watchQuery, vars)
	if err != nil {
		return nil, err
	}
	saves, err := e.activity(ctx, saveQuery, vars)
	if err != nil {
		return nil, err
	}
	signals := ratingSignals(ratings)
	signals = append(signals, watchSignals(watches, now)...)
	for _, row := range saves {
		signals = append(signals, signal{media: row.ref(), weight: weightSave, at: row.time()})
	}
	return signals, nil
}

func (e *Engine) activity(ctx context.Context, query string, vars db.Vars) ([]activityRow, error) {
	rows, err := db.QueryAs[activityRow](ctx, e.database, query, vars)
	if err != nil {
		return nil, fmt.Errorf("read signals: %w", err)
	}
	return rows, nil
}

// ratingSignals scales 1-10 ratings around ratingNeutral into ±weightRatingScale.
func ratingSignals(rows []activityRow) []signal {
	signals := make([]signal, 0, len(rows))
	for _, row := range rows {
		weight := (row.Rating - ratingNeutral) / (10 - ratingNeutral) * weightRatingScale
		signals = append(signals, signal{
			media: row.ref(), weight: weight, at: row.time(), disliked: row.Rating < dislikeBelow,
		})
	}
	return signals
}

func watchSignals(rows []activityRow, now time.Time) []signal {
	signals := make([]signal, 0, len(rows))
	for _, row := range rows {
		weight, counts := watchWeight(row.Progress, row.Duration, row.Completed, now.Sub(row.time()))
		if counts {
			signals = append(signals, signal{media: row.ref(), weight: weight, at: row.time()})
		}
	}
	return signals
}

// watchWeight classifies one watch: completed, partial, or abandoned early.
// counts is false for a fresh playback that may still be starting.
func watchWeight(progressSeconds, durationSeconds float64, completed bool, sinceUpdate time.Duration) (weight float64, counts bool) {
	if completed || watchedFraction(progressSeconds, durationSeconds) >= completedFraction {
		return weightCompletedWatch, true
	}
	if !isEarlyAbandon(progressSeconds, durationSeconds) {
		return weightPartialWatch, true
	}
	if sinceUpdate < abandonGrace {
		return 0, false
	}
	return weightEarlyAbandon, true
}

func watchedFraction(progressSeconds, durationSeconds float64) float64 {
	if durationSeconds <= 0 {
		return 0
	}
	return progressSeconds / durationSeconds
}

func isEarlyAbandon(progressSeconds, durationSeconds float64) bool {
	if progressSeconds < earlyAbandonSeconds {
		return true
	}
	return durationSeconds > 0 && watchedFraction(progressSeconds, durationSeconds) < earlyAbandonFraction
}

// watchState is how far a profile got through a title.
type watchState int

const (
	watchNone watchState = iota
	watchPartial
	watchCompleted
)

// watchStates reads how far the profile got through each title. A series
// with any watched episode counts as partially watched; a rated movie counts
// as seen, since rating implies having watched it.
func (e *Engine) watchStates(ctx context.Context, profileID int) (map[library.MediaRef]watchState, error) {
	vars := db.Vars{"profile": profileID, "since": ""}
	rows, err := e.activity(ctx, watchQuery, vars)
	if err != nil {
		return nil, err
	}
	ratings, err := e.activity(ctx, ratingQuery, vars)
	if err != nil {
		return nil, err
	}
	for _, rating := range ratings {
		rating.Completed = true
		rows = append(rows, rating)
	}
	states := make(map[library.MediaRef]watchState, len(rows))
	for _, row := range rows {
		state := watchPartial
		isFinishedMovie := row.MediaType == library.MediaTypeMovie &&
			(row.Completed || watchedFraction(row.Progress, row.Duration) >= completedFraction)
		if isFinishedMovie {
			state = watchCompleted
		}
		states[row.ref()] = max(states[row.ref()], state)
	}
	return states, nil
}
