package graphql

import (
	"context"

	"github.com/neoworks/otter/internal/api/graphql/model"
	"github.com/neoworks/otter/internal/library"
	"github.com/neoworks/otter/internal/profiles"
)

// minimumResumeSeconds hides playbacks that barely started from "continue".
const minimumResumeSeconds = 60

// historyOverfetch widens the history read, since several entries can
// collapse into one series.
const historyOverfetch = 4

// continueWatching builds the "continue watching" row: unfinished movies,
// and for each series the unfinished episode or the one after the last
// finished one. Most recent first, one entry per title.
func (r *Resolver) continueWatching(ctx context.Context, profileID, limit int) ([]*model.ContinueItem, error) {
	history, err := r.Profiles.History(ctx, profileID, limit*historyOverfetch)
	if err != nil {
		return nil, err
	}
	items := []*model.ContinueItem{}
	seen := map[library.MediaRef]bool{}
	for _, entry := range history {
		if len(items) >= limit {
			break
		}
		item, titleRef, err := r.continueItem(ctx, entry)
		if err != nil {
			return nil, err
		}
		if item == nil || seen[titleRef] {
			continue
		}
		seen[titleRef] = true
		items = append(items, item)
	}
	return items, nil
}

// continueItem turns one history entry into a continue item, or nil when
// there is nothing to continue.
func (r *Resolver) continueItem(ctx context.Context, entry profiles.WatchEntry) (*model.ContinueItem, library.MediaRef, error) {
	if entry.MediaType == library.MediaTypeEpisode {
		return r.continueEpisode(ctx, entry)
	}
	ref := entry.Ref()
	if entry.Completed || entry.ProgressSeconds < minimumResumeSeconds {
		return nil, ref, nil
	}
	title, err := r.Library.Store.Title(ctx, ref)
	if err != nil || title == nil {
		return nil, ref, err
	}
	entryCopy := entry
	return &model.ContinueItem{Title: titleModel(*title), Progress: progressModel(&entryCopy)}, ref, nil
}

func (r *Resolver) continueEpisode(ctx context.Context, entry profiles.WatchEntry) (*model.ContinueItem, library.MediaRef, error) {
	episode, err := r.Library.Store.Episode(ctx, entry.MediaID)
	if err != nil || episode == nil {
		return nil, library.MediaRef{}, err
	}
	seriesRef := library.MediaRef{MediaType: library.MediaTypeSeries, MediaID: episode.SeriesID}
	series, err := r.Library.Store.Title(ctx, seriesRef)
	if err != nil || series == nil {
		return nil, seriesRef, err
	}
	item := &model.ContinueItem{Title: titleModel(*series)}
	if !entry.Completed {
		entryCopy := entry
		item.Episode = episodeModel(*episode)
		item.Progress = progressModel(&entryCopy)
		return item, seriesRef, nil
	}
	next, err := r.Library.Store.NextEpisode(ctx, *episode)
	if err != nil || next == nil {
		return nil, seriesRef, err
	}
	item.Episode = episodeModel(*next)
	return item, seriesRef, nil
}

// historyEntry resolves the title (and episode) behind one history row.
func (r *Resolver) historyEntry(ctx context.Context, entry profiles.WatchEntry) (*model.HistoryEntry, error) {
	entryCopy := entry
	historyEntry := &model.HistoryEntry{Progress: progressModel(&entryCopy)}
	titleRef := entry.Ref()
	if entry.MediaType == library.MediaTypeEpisode {
		episode, err := r.Library.Store.Episode(ctx, entry.MediaID)
		if err != nil || episode == nil {
			return nil, err
		}
		historyEntry.Episode = episodeModel(*episode)
		titleRef = library.MediaRef{MediaType: library.MediaTypeSeries, MediaID: episode.SeriesID}
	}
	title, err := r.Library.Store.Title(ctx, titleRef)
	if err != nil || title == nil {
		return nil, err
	}
	historyEntry.Title = titleModel(*title)
	return historyEntry, nil
}
