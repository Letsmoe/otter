package graphql

import (
	"context"
	"time"

	"github.com/Letsmoe/otter/internal/api/graphql/model"
)

// recentlyWatchedWindow is how long after its last watched episode a series
// still counts as one the profile follows.
const recentlyWatchedWindow = 180 * 24 * time.Hour

// upcomingEpisodes lists the coming episodes of the series the profile has on
// My List or watched lately, soonest first.
func (r *Resolver) upcomingEpisodes(ctx context.Context, profileID, days int) ([]*model.UpcomingEpisode, error) {
	now := time.Now()
	seriesIDs, err := r.Profiles.FollowedSeriesIDs(ctx, profileID, now.Add(-recentlyWatchedWindow))
	if err != nil {
		return nil, err
	}
	today := time.Date(now.Year(), now.Month(), now.Day(), 0, 0, 0, 0, time.UTC)
	upcoming, err := r.Library.UpcomingEpisodes(ctx, seriesIDs, today, days)
	if err != nil {
		return nil, err
	}
	models := make([]*model.UpcomingEpisode, 0, len(upcoming))
	for _, entry := range upcoming {
		models = append(models, &model.UpcomingEpisode{Series: titleModel(entry.Series), Episode: episodeModel(entry.Episode)})
	}
	return models, nil
}
