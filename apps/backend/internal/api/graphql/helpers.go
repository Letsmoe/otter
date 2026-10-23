package graphql

import (
	"context"
	"sort"

	"github.com/neoworks/otter/internal/api/graphql/model"
	"github.com/neoworks/otter/internal/library"
	"github.com/neoworks/otter/internal/profiles"
	"github.com/neoworks/otter/internal/recommend"
)

// valueOr dereferences an optional argument, falling back when it is nil.
func valueOr[Value any](pointer *Value, fallback Value) Value {
	if pointer == nil {
		return fallback
	}
	return *pointer
}

// clamp keeps a requested count between 1 and maximum.
func clamp(value, maximum int) int {
	return max(1, min(value, maximum))
}

func titleRef(title *model.Title) library.MediaRef {
	return library.MediaRef{MediaType: libraryMediaType(title.MediaType), MediaID: title.TmdbID}
}

func profileAndMedia(profileID, mediaID string) (int, library.MediaRef, error) {
	profile, err := parseProfile(profileID)
	if err != nil {
		return 0, library.MediaRef{}, err
	}
	ref, err := parseMedia(mediaID)
	return profile, ref, err
}

// titlesByRef loads the titles of recommendation items in one query.
func (r *Resolver) titlesByRef(ctx context.Context, refs []library.MediaRef) (map[library.MediaRef]*model.Title, error) {
	titles, err := r.Library.Store.Titles(ctx, refs)
	if err != nil {
		return nil, err
	}
	byRef := make(map[library.MediaRef]*model.Title, len(titles))
	for _, title := range titles {
		byRef[library.MediaRef{MediaType: title.MediaType, MediaID: title.TMDBID}] = titleModel(title)
	}
	return byRef, nil
}

func itemRefs(items []recommend.Item) []library.MediaRef {
	refs := make([]library.MediaRef, 0, len(items))
	for _, item := range items {
		refs = append(refs, item.Media)
	}
	return refs
}

func (r *Resolver) recommendationModels(ctx context.Context, items []recommend.Item) ([]*model.Recommendation, error) {
	titles, err := r.titlesByRef(ctx, itemRefs(items))
	if err != nil {
		return nil, err
	}
	models := make([]*model.Recommendation, 0, len(items))
	for _, item := range items {
		title, found := titles[item.Media]
		if !found {
			continue
		}
		models = append(models, &model.Recommendation{
			Title: title, Reason: item.Reason.Text, Source: item.Source, Score: item.Score,
		})
	}
	return models, nil
}

func (r *Resolver) rowModels(ctx context.Context, rows []recommend.Row) ([]*model.RecommendationRow, error) {
	allItems := []recommend.Item{}
	for _, row := range rows {
		allItems = append(allItems, row.Items...)
	}
	titles, err := r.titlesByRef(ctx, itemRefs(allItems))
	if err != nil {
		return nil, err
	}
	models := make([]*model.RecommendationRow, 0, len(rows))
	for _, row := range rows {
		rowTitles := []*model.Title{}
		for _, item := range row.Items {
			if title, found := titles[item.Media]; found {
				rowTitles = append(rowTitles, title)
			}
		}
		models = append(models, &model.RecommendationRow{Key: row.Key, Title: row.Title, Items: rowTitles})
	}
	return models, nil
}

func titleFilter(filter *model.TitleFilter) library.TitleFilter {
	if filter == nil {
		return library.TitleFilter{}
	}
	return library.TitleFilter{
		MinRating:  valueOr(filter.MinRating, 0),
		MinRuntime: valueOr(filter.MinRuntime, 0),
		MaxRuntime: valueOr(filter.MaxRuntime, 0),
		MinSeasons: valueOr(filter.MinSeasons, 0),
		MaxSeasons: valueOr(filter.MaxSeasons, 0),
	}
}

// likedRefs returns the titles rated at least library.WellRatedMinimum,
// latest rating first.
func likedRefs(ratings []profiles.Rating, limit int) []library.MediaRef {
	liked := []profiles.Rating{}
	for _, rating := range ratings {
		if rating.Rating >= library.WellRatedMinimum {
			liked = append(liked, rating)
		}
	}
	sort.Slice(liked, func(first, second int) bool { return liked[first].RatedAt > liked[second].RatedAt })
	refs := []library.MediaRef{}
	for _, rating := range liked {
		if len(refs) == limit {
			break
		}
		refs = append(refs, library.MediaRef{MediaType: rating.MediaType, MediaID: rating.MediaID})
	}
	return refs
}

// inRefOrder lists the loaded titles in the order of refs, skipping missing ones.
func inRefOrder(refs []library.MediaRef, titles map[library.MediaRef]*model.Title) []*model.Title {
	ordered := []*model.Title{}
	for _, ref := range refs {
		if title := titles[ref]; title != nil {
			ordered = append(ordered, title)
		}
	}
	return ordered
}
