package graphql

import (
	"github.com/Letsmoe/otter/internal/api/graphql/model"
	"github.com/Letsmoe/otter/internal/franchise"
)

func collectionModel(guide franchise.Guide) *model.Collection {
	collection := guide.Collection
	return &model.Collection{
		ID:           formatID(kindCollection, collection.TMDBID),
		TmdbID:       collection.TMDBID,
		Name:         collection.Name,
		Overview:     optional(collection.Overview),
		PosterPath:   optional(collection.PosterPath),
		BackdropPath: optional(collection.BackdropPath),
		Parts:        collectionPartModels(guide.Parts),
		WatchedCount: guide.WatchedCount(),
		Next:         nextPartModel(guide),
	}
}

func collectionModels(guides []franchise.Guide) []*model.Collection {
	models := make([]*model.Collection, 0, len(guides))
	for _, guide := range guides {
		models = append(models, collectionModel(guide))
	}
	return models
}

func nextPartModel(guide franchise.Guide) *model.CollectionPart {
	next := guide.Next()
	if next == nil {
		return nil
	}
	return collectionPartModel(*next)
}

func collectionPartModels(parts []franchise.Part) []*model.CollectionPart {
	models := make([]*model.CollectionPart, 0, len(parts))
	for _, part := range parts {
		models = append(models, collectionPartModel(part))
	}
	return models
}

func collectionPartModel(part franchise.Part) *model.CollectionPart {
	return &model.CollectionPart{
		Number:   part.Number,
		Title:    titleModel(part.Title),
		Status:   watchStatusModel(part.Status),
		Progress: part.Progress,
		Released: part.Released,
	}
}

func watchStatusModel(status franchise.Status) model.WatchStatus {
	switch status {
	case franchise.StatusWatched:
		return model.WatchStatusWatched
	case franchise.StatusInProgress:
		return model.WatchStatusInProgress
	}
	return model.WatchStatusNotYet
}
