package profiles

import (
	"context"
	"time"

	"github.com/Letsmoe/otter/internal/db"
)

type seriesIDRow struct {
	SeriesID int `json:"series_id"`
}

// FollowedSeriesIDs lists the series a profile has on My List or watched an
// episode of since the given time, in no particular order.
func (s *Service) FollowedSeriesIDs(ctx context.Context, profileID int, watchedSince time.Time) ([]int, error) {
	rows, err := db.QueryAs[seriesIDRow](ctx, s.database, `
		SELECT media_id AS series_id FROM watchlist
		WHERE profile_id = $profile_id AND media_type = 'series'
		UNION
		SELECT episode.series_id AS series_id FROM watch_history
		JOIN episode ON episode.tmdb_id = watch_history.media_id
		WHERE watch_history.profile_id = $profile_id AND watch_history.media_type = 'episode'
		AND watch_history.updated_at >= $since`,
		db.Vars{"profile_id": profileID, "since": db.FormatTime(watchedSince)})
	if err != nil {
		return nil, err
	}
	seriesIDs := make([]int, 0, len(rows))
	for _, row := range rows {
		seriesIDs = append(seriesIDs, row.SeriesID)
	}
	return seriesIDs, nil
}
