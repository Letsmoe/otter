package graphql

import (
	"context"

	"github.com/Letsmoe/otter/internal/api/graphql/model"
	"github.com/Letsmoe/otter/internal/library"
	"github.com/Letsmoe/otter/internal/profiles"
)

const kindShelf = "shelf"

func parseShelf(id string) (int, error) {
	_, number, err := parseID(id, kindShelf)
	return number, err
}

func parseProfileAndShelf(profileID, shelfID string) (profile, shelf int, err error) {
	profile, err = parseProfile(profileID)
	if err != nil {
		return 0, 0, err
	}
	shelf, err = parseShelf(shelfID)
	return profile, shelf, err
}

// parseShelfTitles parses the movie and series ids of a reorder request.
func parseShelfTitles(mediaIDs []string) ([]library.MediaRef, error) {
	refs := make([]library.MediaRef, 0, len(mediaIDs))
	for _, id := range mediaIDs {
		ref, err := parseMedia(id, library.MediaTypeMovie, library.MediaTypeSeries)
		if err != nil {
			return nil, err
		}
		refs = append(refs, ref)
	}
	return refs, nil
}

func parseShelves(shelfIDs []string) ([]int, error) {
	numbers := make([]int, 0, len(shelfIDs))
	for _, id := range shelfIDs {
		number, err := parseShelf(id)
		if err != nil {
			return nil, err
		}
		numbers = append(numbers, number)
	}
	return numbers, nil
}

// shelfModels loads the titles of the shelves in one query and maps them.
func (r *Resolver) shelfModels(ctx context.Context, shelves []profiles.Shelf) ([]*model.Shelf, error) {
	refs := []library.MediaRef{}
	for _, shelf := range shelves {
		refs = append(refs, shelf.Titles...)
	}
	titlesByRef, err := r.titlesByRef(ctx, refs)
	if err != nil {
		return nil, err
	}
	models := make([]*model.Shelf, 0, len(shelves))
	for _, shelf := range shelves {
		models = append(models, shelfModel(shelf, titlesByRef))
	}
	return models, nil
}

// shelfModel maps a shelf; titles that are no longer stored are left out.
func shelfModel(shelf profiles.Shelf, titlesByRef map[library.MediaRef]*model.Title) *model.Shelf {
	titles := make([]*model.Title, 0, len(shelf.Titles))
	for _, ref := range shelf.Titles {
		title, found := titlesByRef[ref]
		if found {
			titles = append(titles, title)
		}
	}
	return &model.Shelf{ID: formatID(kindShelf, shelf.ID), Name: shelf.Name, Titles: titles}
}

func (r *Resolver) singleShelfModel(ctx context.Context, shelf *profiles.Shelf) (*model.Shelf, error) {
	models, err := r.shelfModels(ctx, []profiles.Shelf{*shelf})
	if err != nil {
		return nil, err
	}
	return models[0], nil
}
