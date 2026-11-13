package neoworks

import (
	"context"
	"fmt"
	"strconv"

	"github.com/Letsmoe/otter/internal/db"
)

// Watch progress, My List and ratings as rows (packages/otter-schema/otter.schema).
var itemSpecs = []itemSpec{watchProgressSpec, watchlistSpec, ratingSpec}

// watchProgressSpec: the newer updatedAt wins, and finished stays finished.
var watchProgressSpec = itemSpec{
	table:      tableWatchProgress,
	localTable: "watch_history",
	timeField:  "updatedAt",
	localTime:  func(record db.Row) string { return timestampOf(textOf(record["updated_at"])) },
	content: func(record db.Row) map[string]any {
		return map[string]any{
			"mediaType": textOf(record["media_type"]),
			"tmdbId":    tmdbIDText(record),
			"position":  nanosecondsText(numberOf(record["progress_seconds"])),
			"runtime":   nanosecondsText(numberOf(record["duration_seconds"])),
			"completed": truthOf(record["completed"]),
			"startedAt": timestampOf(textOf(record["started_at"])),
			"updatedAt": timestampOf(textOf(record["updated_at"])),
		}
	},
	apply: func(ctx context.Context, database *db.DB, key itemKey, content map[string]any, local db.Row) error {
		completed, _ := content["completed"].(bool)
		if local != nil && truthOf(local["completed"]) {
			completed = true
		}
		_, err := database.Exec(ctx,
			`INSERT INTO watch_history (profile_id, media_type, media_id, progress_seconds, duration_seconds, completed, started_at, updated_at)
			 VALUES ($profileId, $mediaType, $mediaId, $progress, $duration, $completed, $startedAt, $updatedAt)
			 ON CONFLICT (profile_id, media_type, media_id) DO UPDATE SET progress_seconds = excluded.progress_seconds,
			   duration_seconds = excluded.duration_seconds, completed = excluded.completed, updated_at = excluded.updated_at`,
			itemVars(key, db.Vars{
				"progress": secondsOf(content["position"]), "duration": secondsOf(content["runtime"]), "completed": completed,
				"startedAt": timestampOf(textOf(content["startedAt"])), "updatedAt": timestampOf(textOf(content["updatedAt"])),
			}))
		return err
	},
}

// watchlistSpec: an entry exists or not; addedAt only orders duplicates.
var watchlistSpec = itemSpec{
	table:      tableWatchlistEntry,
	localTable: "watchlist",
	timeField:  "addedAt",
	localTime:  func(record db.Row) string { return timestampOf(textOf(record["added_at"])) },
	content: func(record db.Row) map[string]any {
		return map[string]any{
			"mediaType": textOf(record["media_type"]),
			"tmdbId":    tmdbIDText(record),
			"addedAt":   timestampOf(textOf(record["added_at"])),
		}
	},
	apply: func(ctx context.Context, database *db.DB, key itemKey, content map[string]any, _ db.Row) error {
		_, err := database.Exec(ctx,
			`INSERT INTO watchlist (profile_id, media_type, media_id, added_at) VALUES ($profileId, $mediaType, $mediaId, $addedAt)
			 ON CONFLICT (profile_id, media_type, media_id) DO UPDATE SET added_at = excluded.added_at`,
			itemVars(key, db.Vars{"addedAt": timestampOf(textOf(content["addedAt"]))}))
		return err
	},
}

// ratingSpec: the newer ratedAt wins.
var ratingSpec = itemSpec{
	table:      tableRating,
	localTable: "user_rating",
	timeField:  "ratedAt",
	localTime:  func(record db.Row) string { return timestampOf(textOf(record["rated_at"])) },
	content: func(record db.Row) map[string]any {
		return map[string]any{
			"mediaType": textOf(record["media_type"]),
			"tmdbId":    tmdbIDText(record),
			"value":     numberOf(record["rating"]),
			"ratedAt":   timestampOf(textOf(record["rated_at"])),
		}
	},
	apply: func(ctx context.Context, database *db.DB, key itemKey, content map[string]any, _ db.Row) error {
		value, _ := content["value"].(float64)
		_, err := database.Exec(ctx,
			`INSERT INTO user_rating (profile_id, media_type, media_id, rating, rated_at) VALUES ($profileId, $mediaType, $mediaId, $rating, $ratedAt)
			 ON CONFLICT (profile_id, media_type, media_id) DO UPDATE SET rating = excluded.rating, rated_at = excluded.rated_at`,
			itemVars(key, db.Vars{"rating": value, "ratedAt": timestampOf(textOf(content["ratedAt"]))}))
		return err
	},
}

// itemVars adds the record's key to the values of a statement.
func itemVars(key itemKey, values db.Vars) db.Vars {
	mediaID, _ := mediaIDOf(key)
	values["profileId"] = key.profileID
	values["mediaType"] = key.mediaType
	values["mediaId"] = mediaID
	return values
}

func textOf(value any) string {
	if value == nil {
		return ""
	}
	return fmt.Sprint(value)
}

func numberOf(value any) float64 {
	switch number := value.(type) {
	case float64:
		return number
	case int64:
		return float64(number)
	case int:
		return float64(number)
	}
	return 0
}

func truthOf(value any) bool {
	switch flag := value.(type) {
	case bool:
		return flag
	case int64:
		return flag != 0
	}
	return false
}

// nanosecondsText writes seconds as a duration field: nanoseconds as a decimal string.
func nanosecondsText(seconds float64) string {
	return strconv.FormatInt(int64(seconds*1e9), 10)
}

func secondsOf(value any) float64 {
	nanoseconds, err := strconv.ParseInt(textOf(value), 10, 64)
	if err != nil {
		return 0
	}
	return float64(nanoseconds) / 1e9
}
