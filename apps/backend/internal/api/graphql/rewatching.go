package graphql

import (
	"context"

	"github.com/Letsmoe/otter/internal/api/graphql/model"
	"github.com/Letsmoe/otter/internal/library"
	"github.com/Letsmoe/otter/internal/profiles"
)

func rewatchModel(rewatch *profiles.Rewatch) *model.Rewatch {
	if rewatch == nil {
		return nil
	}
	return &model.Rewatch{StartedAt: rewatch.StartedAt, Finished: !rewatch.Active()}
}

// startRewatch begins a rewatch of a movie or series the title id names.
func (r *Resolver) startRewatch(ctx context.Context, profileID, titleID string) (*model.Rewatch, error) {
	profile, err := parseProfile(profileID)
	if err != nil {
		return nil, err
	}
	title, err := parseMedia(titleID, library.MediaTypeMovie, library.MediaTypeSeries)
	if err != nil {
		return nil, err
	}
	rewatch, err := r.Profiles.StartRewatch(ctx, profile, title)
	if err != nil {
		return nil, err
	}
	return rewatchModel(rewatch), nil
}

func (r *Resolver) stopRewatch(ctx context.Context, profileID, titleID string) (bool, error) {
	profile, err := parseProfile(profileID)
	if err != nil {
		return false, err
	}
	title, err := parseMedia(titleID, library.MediaTypeMovie, library.MediaTypeSeries)
	if err != nil {
		return false, err
	}
	return true, r.Profiles.StopRewatch(ctx, profile, title)
}

// continueRewatching builds the "continue rewatch" row from the runs still
// going, most recent first. A run that has not played anything yet offers
// its first episode, or the movie itself.
func (r *Resolver) continueRewatching(ctx context.Context, profileID, limit int) ([]*model.ContinueItem, error) {
	runs, err := r.Profiles.ActiveRewatches(ctx, profileID, limit)
	if err != nil {
		return nil, err
	}
	items := []*model.ContinueItem{}
	for _, run := range runs {
		item, err := r.rewatchItem(ctx, run)
		if err != nil {
			return nil, err
		}
		if item != nil {
			items = append(items, item)
		}
	}
	return items, nil
}

func (r *Resolver) rewatchItem(ctx context.Context, run profiles.Rewatch) (*model.ContinueItem, error) {
	title, err := r.Library.Store.Title(ctx, run.Title())
	if err != nil || title == nil {
		return nil, err
	}
	if run.MediaType == library.MediaTypeMovie {
		entry, err := r.Profiles.WatchEntry(ctx, run.ProfileID, run.Title())
		if err != nil {
			return nil, err
		}
		return &model.ContinueItem{Title: titleModel(*title), Progress: progressModel(entry)}, nil
	}
	return r.rewatchSeriesItem(ctx, run, title)
}

func (r *Resolver) rewatchSeriesItem(ctx context.Context, run profiles.Rewatch, series *library.Title) (*model.ContinueItem, error) {
	item := &model.ContinueItem{Title: titleModel(*series)}
	latest, err := r.Profiles.LatestRewatchEpisode(ctx, run.ProfileID, run.MediaID)
	if err != nil {
		return nil, err
	}
	if latest == nil {
		first, err := r.Library.Store.FirstEpisode(ctx, run.MediaID)
		return attachEpisode(item, first, err)
	}
	episode, err := r.Library.Store.Episode(ctx, latest.MediaID)
	if err != nil || episode == nil {
		return nil, err
	}
	if !latest.Completed {
		item.Progress = progressModel(latest)
		return attachEpisode(item, episode, nil)
	}
	next, err := r.Library.Store.NextEpisode(ctx, *episode)
	return attachEpisode(item, next, err)
}

// attachEpisode sets the item's episode; nil when the store found none.
func attachEpisode(item *model.ContinueItem, episode *library.Episode, err error) (*model.ContinueItem, error) {
	if err != nil || episode == nil {
		return nil, err
	}
	item.Episode = episodeModel(*episode)
	return item, nil
}
