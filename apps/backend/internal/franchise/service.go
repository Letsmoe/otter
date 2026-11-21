package franchise

import (
	"context"
	"fmt"
	"slices"
	"time"

	"github.com/Letsmoe/otter/internal/db"
	"github.com/Letsmoe/otter/internal/library"
)

const (
	// minimumParts is the film count that makes a collection a franchise
	// worth a guide.
	minimumParts = 2
	// franchiseScanLimit bounds how many stored collections are considered.
	franchiseScanLimit = 60
)

// Service reads guides from the library and a profile's watch history.
type Service struct {
	library  *library.Service
	database *db.DB
	// now is the clock, swappable in tests.
	now func() time.Time
}

func NewService(libraryService *library.Service, database *db.DB) *Service {
	return &Service{library: libraryService, database: database, now: time.Now}
}

// Guide returns one collection's guide for a profile, fetching the film list
// from TMDB when it isn't stored yet.
func (s *Service) Guide(ctx context.Context, collectionID, profileID int) (*Guide, error) {
	collection, titles, err := s.library.EnsureCollection(ctx, collectionID)
	if err != nil {
		return nil, err
	}
	guide, err := s.guideOf(ctx, *collection, titles, profileID)
	if err != nil {
		return nil, err
	}
	return &guide, nil
}

// Franchises lists the stored franchises as guides: the ones the profile is
// in the middle of first, then untouched ones by popularity, finished ones last.
func (s *Service) Franchises(ctx context.Context, profileID, limit int) ([]Guide, error) {
	collections, err := s.library.Store.FetchedCollections(ctx, minimumParts, franchiseScanLimit)
	if err != nil {
		return nil, err
	}
	guides := make([]Guide, 0, len(collections))
	for _, collection := range collections {
		titles, err := s.library.Store.Titles(ctx, library.MovieRefs(collection.PartIDs))
		if err != nil {
			return nil, err
		}
		guide, err := s.guideOf(ctx, collection, titles, profileID)
		if err != nil {
			return nil, err
		}
		guides = append(guides, guide)
	}
	slices.SortStableFunc(guides, compareGuides)
	if len(guides) > limit {
		guides = guides[:limit]
	}
	return guides, nil
}

func (s *Service) guideOf(ctx context.Context, collection library.Collection, titles []library.Title, profileID int) (Guide, error) {
	watches, err := s.watchesOf(ctx, profileID, titles)
	if err != nil {
		return Guide{}, err
	}
	return NewGuide(collection, titles, watches, s.now()), nil
}

// compareGuides puts guides in progress first, then untouched, then finished.
func compareGuides(left, right Guide) int {
	return guideRank(left) - guideRank(right)
}

func guideRank(guide Guide) int {
	if guide.Next() == nil {
		return 2
	}
	if guide.Started() {
		return 0
	}
	return 1
}

type watchRow struct {
	MediaID         int     `json:"media_id"`
	ProgressSeconds float64 `json:"progress_seconds"`
	DurationSeconds float64 `json:"duration_seconds"`
	Completed       bool    `json:"completed"`
}

// watchesOf reads the profile's progress on the films. A rated film counts
// as seen, like in the recommendation engine.
func (s *Service) watchesOf(ctx context.Context, profileID int, titles []library.Title) (map[int]Watch, error) {
	vars := db.Vars{"profile": profileID, "ids": idsOf(titles)}
	rows, err := db.QueryAs[watchRow](ctx, s.database, `SELECT media_id, progress_seconds,
		duration_seconds, completed FROM watch_history WHERE profile_id = $profile
		AND media_type = 'movie' AND media_id IN (SELECT value FROM json_each($ids))`, vars)
	if err != nil {
		return nil, fmt.Errorf("read watches: %w", err)
	}
	watches := map[int]Watch{}
	for _, row := range rows {
		watches[row.MediaID] = Watch{Completed: row.Completed, ProgressSeconds: row.ProgressSeconds, DurationSeconds: row.DurationSeconds}
	}
	return s.addRatedAsSeen(ctx, vars, watches)
}

func (s *Service) addRatedAsSeen(ctx context.Context, vars db.Vars, watches map[int]Watch) (map[int]Watch, error) {
	rated, err := db.QueryAs[watchRow](ctx, s.database, `SELECT media_id FROM user_rating
		WHERE profile_id = $profile AND media_type = 'movie'
		AND media_id IN (SELECT value FROM json_each($ids))`, vars)
	if err != nil {
		return nil, fmt.Errorf("read ratings: %w", err)
	}
	for _, row := range rated {
		watches[row.MediaID] = Watch{Completed: true}
	}
	return watches, nil
}

func idsOf(titles []library.Title) []int {
	ids := make([]int, 0, len(titles))
	for _, title := range titles {
		ids = append(ids, title.TMDBID)
	}
	return ids
}
