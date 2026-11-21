package graphql

import (
	"context"

	"github.com/Letsmoe/otter/internal/api/graphql/model"
	"github.com/Letsmoe/otter/internal/library"
)

// How many candidate films are looked at: each one without a stored runtime costs a TMDB request.
const filmCandidateLimit = 40

// timeSlot suggests what fits into [minutes]: what the profile is in the middle of, then short films.
// A child profile's age limit applies to every candidate.
func (r *Resolver) timeSlot(ctx context.Context, profileID, minutes, limit int) ([]*model.TimeSlotItem, error) {
	gate, err := r.gateForProfile(ctx, profileID)
	if err != nil {
		return nil, err
	}
	continuing, err := r.continueWatching(ctx, profileID, 20)
	if err != nil {
		return nil, err
	}
	continuing, err = r.gatedContinueItems(ctx, gate, continuing)
	if err != nil {
		return nil, err
	}
	items := continuingThatFits(continuing, minutes)
	films, err := r.shortFilms(ctx, profileID, minutes, continuing, gate)
	if err != nil {
		return nil, err
	}
	items = append(items, films...)
	return items[:min(len(items), limit)], nil
}

// continuingThatFits keeps the series' next episodes and the unfinished movies that take at most [minutes] more.
func continuingThatFits(continuing []*model.ContinueItem, minutes int) []*model.TimeSlotItem {
	items := []*model.TimeSlotItem{}
	for _, entry := range continuing {
		remaining := remainingMinutes(entry)
		if !library.FitsMinutes(remaining, minutes) {
			continue
		}
		items = append(items, &model.TimeSlotItem{Title: entry.Title, Episode: entry.Episode, Minutes: remaining, Reason: continuingReason(entry)})
	}
	return items
}

func remainingMinutes(entry *model.ContinueItem) int {
	runtime := 0
	if entry.Episode != nil {
		runtime = valueOr(entry.Episode.Runtime, 0)
	} else {
		runtime = valueOr(entry.Title.Runtime, 0)
	}
	watchedSeconds := 0.0
	if entry.Progress != nil && !entry.Progress.Completed {
		watchedSeconds = entry.Progress.ProgressSeconds
	}
	return library.RemainingMinutes(runtime, watchedSeconds)
}

func continuingReason(entry *model.ContinueItem) string {
	if entry.Progress != nil && !entry.Progress.Completed {
		return "Pick up where you stopped"
	}
	return "Next episode"
}

// shortFilms are the unseen movies of My List and the recommendations that fit, longest first.
func (r *Resolver) shortFilms(ctx context.Context, profileID, minutes int, continuing []*model.ContinueItem, gate ageGate) ([]*model.TimeSlotItem, error) {
	listed, err := r.listedFilms(ctx, profileID)
	if err != nil {
		return nil, err
	}
	listed, err = r.gatedRefs(ctx, gate, listed)
	if err != nil {
		return nil, err
	}
	recommended, err := r.recommendedFilms(ctx, profileID)
	if err != nil {
		return nil, err
	}
	recommended, err = r.gatedRefs(ctx, gate, recommended)
	if err != nil {
		return nil, err
	}
	started := startedTitles(continuing)
	listedItems, err := r.filmItems(ctx, listed, minutes, "From My List", started)
	if err != nil {
		return nil, err
	}
	recommendedItems, err := r.filmItems(ctx, recommended, minutes, "Recommended", started)
	if err != nil {
		return nil, err
	}
	return append(listedItems, recommendedItems...), nil
}

func startedTitles(continuing []*model.ContinueItem) map[library.MediaRef]bool {
	started := map[library.MediaRef]bool{}
	for _, entry := range continuing {
		started[titleRef(entry.Title)] = true
	}
	return started
}

// listedFilms are the movies on My List the profile hasn't finished.
func (r *Resolver) listedFilms(ctx context.Context, profileID int) ([]library.MediaRef, error) {
	entries, err := r.Profiles.Watchlist(ctx, profileID)
	if err != nil {
		return nil, err
	}
	finished, err := r.finishedTitles(ctx, profileID)
	if err != nil {
		return nil, err
	}
	refs := []library.MediaRef{}
	for _, entry := range entries {
		ref := library.MediaRef{MediaType: entry.MediaType, MediaID: entry.MediaID}
		if entry.MediaType == library.MediaTypeMovie && !finished[ref] {
			refs = append(refs, ref)
		}
	}
	return refs, nil
}

func (r *Resolver) finishedTitles(ctx context.Context, profileID int) (map[library.MediaRef]bool, error) {
	history, err := r.Profiles.History(ctx, profileID, 500)
	if err != nil {
		return nil, err
	}
	finished := map[library.MediaRef]bool{}
	for _, entry := range history {
		if entry.Completed {
			finished[entry.Ref()] = true
		}
	}
	return finished, nil
}

func (r *Resolver) recommendedFilms(ctx context.Context, profileID int) ([]library.MediaRef, error) {
	items, err := r.Recommender.Feed(ctx, profileID, library.MediaTypeMovie, filmCandidateLimit, 0, false)
	if err != nil {
		return nil, err
	}
	return itemRefs(items), nil
}

// filmItems turns the refs that fit into suggestions, skipping titles already started.
func (r *Resolver) filmItems(ctx context.Context, refs []library.MediaRef, minutes int, reason string, started map[library.MediaRef]bool) ([]*model.TimeSlotItem, error) {
	titles, err := r.Library.Store.Titles(ctx, refs[:min(len(refs), filmCandidateLimit)])
	if err != nil {
		return nil, err
	}
	// Runtime 1 and up: a title whose runtime is unknown doesn't pass.
	titles, err = r.Library.FilterTitles(ctx, titles, library.TitleFilter{MinRuntime: 1, MaxRuntime: minutes})
	if err != nil {
		return nil, err
	}
	items := []*model.TimeSlotItem{}
	for _, title := range titleModels(library.FilmsThatFit(titles, minutes)) {
		if started[titleRef(title)] {
			continue
		}
		items = append(items, &model.TimeSlotItem{Title: title, Minutes: valueOr(title.Runtime, 0), Reason: reason})
	}
	return items, nil
}
