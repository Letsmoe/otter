package profiles

import (
	"context"
	"errors"
	"fmt"
	"slices"
	"strings"
	"unicode/utf8"

	"github.com/Letsmoe/otter/internal/db"
	"github.com/Letsmoe/otter/internal/library"
)

const (
	maxShelfNameLength   = 40
	maxShelvesPerProfile = 30
	maxTitlesPerShelf    = 200
)

var (
	ErrShelfNotFound    = errors.New("shelf not found")
	ErrInvalidShelfName = fmt.Errorf("a shelf name has 1 to %d characters", maxShelfNameLength)
	ErrTooManyShelves   = fmt.Errorf("a profile can have at most %d shelves", maxShelvesPerProfile)
	ErrShelfFull        = fmt.Errorf("a shelf holds at most %d titles", maxTitlesPerShelf)
)

// Shelf is one of a profile's own Home rows: a named, ordered list of titles.
type Shelf struct {
	ID        int                `json:"id"`
	ProfileID int                `json:"profile_id"`
	Name      string             `json:"name"`
	Position  int                `json:"position"`
	Titles    []library.MediaRef `json:"-"`
}

type shelfTitleRow struct {
	ShelfID   int               `json:"shelf_id"`
	MediaType library.MediaType `json:"media_type"`
	MediaID   int               `json:"media_id"`
}

// Shelves lists the profile's shelves in their order, each with its titles in order.
func (s *Service) Shelves(ctx context.Context, profileID int) ([]Shelf, error) {
	shelves, err := db.QueryAs[Shelf](ctx, s.database, `SELECT id, profile_id, name, position FROM shelf
		WHERE profile_id = $profile_id ORDER BY position, id`, db.Vars{"profile_id": profileID})
	if err != nil {
		return nil, err
	}
	rows, err := db.QueryAs[shelfTitleRow](ctx, s.database, `SELECT shelf_id, media_type, media_id FROM shelf_title
		WHERE shelf_id IN (SELECT id FROM shelf WHERE profile_id = $profile_id) ORDER BY shelf_id, position`,
		db.Vars{"profile_id": profileID})
	if err != nil {
		return nil, err
	}
	return withTitles(shelves, rows), nil
}

func withTitles(shelves []Shelf, rows []shelfTitleRow) []Shelf {
	indexByID := make(map[int]int, len(shelves))
	for index, shelf := range shelves {
		indexByID[shelf.ID] = index
	}
	for _, row := range rows {
		index := indexByID[row.ShelfID]
		ref := library.MediaRef{MediaType: row.MediaType, MediaID: row.MediaID}
		shelves[index].Titles = append(shelves[index].Titles, ref)
	}
	return shelves
}

// Shelf returns one shelf of the profile with its titles.
func (s *Service) Shelf(ctx context.Context, profileID, shelfID int) (*Shelf, error) {
	shelves, err := s.Shelves(ctx, profileID)
	if err != nil {
		return nil, err
	}
	for index := range shelves {
		if shelves[index].ID == shelfID {
			return &shelves[index], nil
		}
	}
	return nil, ErrShelfNotFound
}

// CreateShelf adds an empty shelf after the profile's others.
func (s *Service) CreateShelf(ctx context.Context, profileID int, name string) (*Shelf, error) {
	cleaned, err := cleanShelfName(name)
	if err != nil {
		return nil, err
	}
	count, err := s.database.Int(ctx, `SELECT COUNT(*) FROM shelf WHERE profile_id = $profile_id`, db.Vars{"profile_id": profileID})
	if err != nil {
		return nil, err
	}
	if count >= maxShelvesPerProfile {
		return nil, ErrTooManyShelves
	}
	shelfID, err := s.database.Int(ctx, `INSERT INTO shelf (profile_id, name, position, created_at)
		VALUES ($profile_id, $name, $position, $now) RETURNING id`,
		db.Vars{"profile_id": profileID, "name": cleaned, "position": count, "now": db.Now()})
	if err != nil {
		return nil, err
	}
	return s.Shelf(ctx, profileID, shelfID)
}

// RenameShelf changes a shelf's name.
func (s *Service) RenameShelf(ctx context.Context, profileID, shelfID int, name string) (*Shelf, error) {
	cleaned, err := cleanShelfName(name)
	if err != nil {
		return nil, err
	}
	changed, err := s.database.Exec(ctx, `UPDATE shelf SET name = $name WHERE id = $id AND profile_id = $profile_id`,
		db.Vars{"name": cleaned, "id": shelfID, "profile_id": profileID})
	if err != nil {
		return nil, err
	}
	if changed == 0 {
		return nil, ErrShelfNotFound
	}
	return s.Shelf(ctx, profileID, shelfID)
}

// DeleteShelf removes a shelf and its titles, and closes the gap in the order.
func (s *Service) DeleteShelf(ctx context.Context, profileID, shelfID int) error {
	shelf, err := s.Shelf(ctx, profileID, shelfID)
	if err != nil {
		return err
	}
	return s.database.Tx(ctx, func(tx *db.DB) error {
		if _, err := tx.Exec(ctx, `DELETE FROM shelf_title WHERE shelf_id = $id`, db.Vars{"id": shelf.ID}); err != nil {
			return err
		}
		if _, err := tx.Exec(ctx, `DELETE FROM shelf WHERE id = $id`, db.Vars{"id": shelf.ID}); err != nil {
			return err
		}
		return renumberShelves(ctx, tx, profileID)
	})
}

func renumberShelves(ctx context.Context, tx *db.DB, profileID int) error {
	_, err := tx.Exec(ctx, `UPDATE shelf SET position = (
			SELECT COUNT(*) FROM shelf AS earlier WHERE earlier.profile_id = shelf.profile_id
			AND (earlier.position < shelf.position OR (earlier.position = shelf.position AND earlier.id < shelf.id)))
		WHERE profile_id = $profile_id`, db.Vars{"profile_id": profileID})
	return err
}

// AddToShelf puts a movie or series at the end of a shelf; a title already on it stays where it is.
func (s *Service) AddToShelf(ctx context.Context, profileID, shelfID int, ref library.MediaRef) error {
	shelf, err := s.Shelf(ctx, profileID, shelfID)
	if err != nil {
		return err
	}
	if slices.Contains(shelf.Titles, ref) {
		return nil
	}
	if len(shelf.Titles) >= maxTitlesPerShelf {
		return ErrShelfFull
	}
	vars := refVars(profileID, ref)
	vars["shelf_id"] = shelfID
	vars["position"] = len(shelf.Titles)
	_, err = s.database.Exec(ctx, `INSERT INTO shelf_title (shelf_id, media_type, media_id, position, added_at)
		VALUES ($shelf_id, $media_type, $media_id, $position, $now)`, vars)
	return err
}

// RemoveFromShelf takes a title off a shelf and closes the gap in its order.
func (s *Service) RemoveFromShelf(ctx context.Context, profileID, shelfID int, ref library.MediaRef) error {
	shelf, err := s.Shelf(ctx, profileID, shelfID)
	if err != nil {
		return err
	}
	remaining := slices.DeleteFunc(slices.Clone(shelf.Titles), func(title library.MediaRef) bool { return title == ref })
	return s.database.Tx(ctx, func(tx *db.DB) error {
		vars := refVars(profileID, ref)
		vars["shelf_id"] = shelf.ID
		_, err := tx.Exec(ctx, `DELETE FROM shelf_title WHERE shelf_id = $shelf_id
			AND media_type = $media_type AND media_id = $media_id`, vars)
		if err != nil {
			return err
		}
		return writeTitleOrder(ctx, tx, shelf.ID, remaining)
	})
}

// ReorderShelves sets the order of the profile's shelves. Shelves left out of
// shelfIDs follow in their old order; ids that aren't the profile's are ignored.
func (s *Service) ReorderShelves(ctx context.Context, profileID int, shelfIDs []int) error {
	shelves, err := s.Shelves(ctx, profileID)
	if err != nil {
		return err
	}
	current := make([]int, 0, len(shelves))
	for _, shelf := range shelves {
		current = append(current, shelf.ID)
	}
	ordered := reorder(current, shelfIDs)
	return s.database.Tx(ctx, func(tx *db.DB) error {
		for position, shelfID := range ordered {
			_, err := tx.Exec(ctx, `UPDATE shelf SET position = $position WHERE id = $id`,
				db.Vars{"position": position, "id": shelfID})
			if err != nil {
				return err
			}
		}
		return nil
	})
}

// ReorderShelfTitles sets the order of a shelf's titles, like ReorderShelves.
func (s *Service) ReorderShelfTitles(ctx context.Context, profileID, shelfID int, refs []library.MediaRef) error {
	shelf, err := s.Shelf(ctx, profileID, shelfID)
	if err != nil {
		return err
	}
	ordered := reorder(shelf.Titles, refs)
	return s.database.Tx(ctx, func(tx *db.DB) error {
		return writeTitleOrder(ctx, tx, shelf.ID, ordered)
	})
}

func writeTitleOrder(ctx context.Context, tx *db.DB, shelfID int, refs []library.MediaRef) error {
	for position, ref := range refs {
		_, err := tx.Exec(ctx, `UPDATE shelf_title SET position = $position WHERE shelf_id = $shelf_id
			AND media_type = $media_type AND media_id = $media_id`,
			db.Vars{"position": position, "shelf_id": shelfID, "media_type": ref.MediaType, "media_id": ref.MediaID})
		if err != nil {
			return err
		}
	}
	return nil
}

// reorder returns current in the order of wanted: items of wanted that are in
// current come first (once each), the rest follow in their old order.
func reorder[Item comparable](current, wanted []Item) []Item {
	ordered := make([]Item, 0, len(current))
	for _, item := range wanted {
		if slices.Contains(current, item) && !slices.Contains(ordered, item) {
			ordered = append(ordered, item)
		}
	}
	for _, item := range current {
		if !slices.Contains(ordered, item) {
			ordered = append(ordered, item)
		}
	}
	return ordered
}

func cleanShelfName(name string) (string, error) {
	cleaned := strings.TrimSpace(name)
	length := utf8.RuneCountInString(cleaned)
	if length == 0 || length > maxShelfNameLength {
		return "", ErrInvalidShelfName
	}
	return cleaned, nil
}

// DeleteShelves removes all shelves of a profile inside a transaction; part of deleting the profile.
func DeleteShelves(ctx context.Context, tx *db.DB, profileID int) error {
	_, err := tx.Exec(ctx, `DELETE FROM shelf_title WHERE shelf_id IN (SELECT id FROM shelf WHERE profile_id = $id)`, db.Vars{"id": profileID})
	if err != nil {
		return err
	}
	_, err = tx.Exec(ctx, `DELETE FROM shelf WHERE profile_id = $id`, db.Vars{"id": profileID})
	return err
}
