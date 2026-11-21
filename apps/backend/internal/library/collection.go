package library

import (
	"cmp"
	"context"
	"fmt"
	"slices"
	"time"

	"github.com/Letsmoe/otter/internal/db"
	"github.com/Letsmoe/otter/internal/tmdb"
)

// collectionDetailTTL is how long a collection's film list counts as fresh;
// new instalments are announced rarely.
const collectionDetailTTL = 7 * 24 * time.Hour

// Collection is a TMDB franchise: films that belong together.
type Collection struct {
	TMDBID       int    `json:"tmdb_id"`
	Name         string `json:"name"`
	Overview     string `json:"overview"`
	PosterPath   string `json:"poster_path"`
	BackdropPath string `json:"backdrop_path"`
	// PartIDs are the TMDB ids of the films in release order.
	PartIDs         []int  `json:"parts"`
	DetailFetchedAt string `json:"detail_fetched_at"`
}

// SaveCollectionSummary records a collection seen on a movie's detail. It
// keeps the film list of an already fetched collection.
func (s *Store) SaveCollectionSummary(ctx context.Context, summary Collection) error {
	_, err := s.database.Exec(ctx, `INSERT INTO collection (tmdb_id, name, poster_path, backdrop_path, created_at)
		VALUES ($tmdb_id, $name, $poster_path, $backdrop_path, $now)
		ON CONFLICT (tmdb_id) DO UPDATE SET name = excluded.name,
			poster_path = excluded.poster_path, backdrop_path = excluded.backdrop_path`,
		collectionVars(summary))
	return err
}

// SaveCollection writes a fully fetched collection.
func (s *Store) SaveCollection(ctx context.Context, collection Collection) error {
	_, err := s.database.Exec(ctx, `INSERT INTO collection (tmdb_id, name, overview, poster_path,
		backdrop_path, parts, detail_fetched_at, created_at)
		VALUES ($tmdb_id, $name, $overview, $poster_path, $backdrop_path, $parts, $now, $now)
		ON CONFLICT (tmdb_id) DO UPDATE SET name = excluded.name, overview = excluded.overview,
			poster_path = excluded.poster_path, backdrop_path = excluded.backdrop_path,
			parts = excluded.parts, detail_fetched_at = excluded.detail_fetched_at`,
		collectionVars(collection))
	return err
}

// LinkMoviesToCollection marks stored films as parts of a collection, so a
// film only seen in a list still knows its franchise.
func (s *Store) LinkMoviesToCollection(ctx context.Context, collectionID int, movieIDs []int) error {
	_, err := s.database.Exec(ctx, `UPDATE movie SET collection_id = $collection
		WHERE tmdb_id IN (SELECT value FROM json_each($ids))`,
		db.Vars{"collection": collectionID, "ids": movieIDs})
	return err
}

func collectionVars(collection Collection) db.Vars {
	partIDs := collection.PartIDs
	if partIDs == nil {
		partIDs = []int{}
	}
	return db.Vars{
		"tmdb_id": collection.TMDBID, "name": collection.Name, "overview": collection.Overview,
		"poster_path": nullable(collection.PosterPath), "backdrop_path": nullable(collection.BackdropPath),
		"parts": partIDs, "now": db.Now(),
	}
}

const collectionColumns = `tmdb_id, name, COALESCE(overview, '') AS overview,
	COALESCE(poster_path, '') AS poster_path, COALESCE(backdrop_path, '') AS backdrop_path,
	parts, COALESCE(detail_fetched_at, '') AS detail_fetched_at`

// Collection loads one collection; nil when it is not stored.
func (s *Store) Collection(ctx context.Context, collectionID int) (*Collection, error) {
	return db.QueryOneAs[Collection](ctx, s.database,
		"SELECT "+collectionColumns+" FROM collection WHERE tmdb_id = $id", db.Vars{"id": collectionID})
}

// FetchedCollections lists the collections whose film list is stored and has
// at least minimumParts films, most popular first (by their films).
func (s *Store) FetchedCollections(ctx context.Context, minimumParts, limit int) ([]Collection, error) {
	return db.QueryAs[Collection](ctx, s.database, `SELECT `+collectionColumns+` FROM collection
		WHERE detail_fetched_at IS NOT NULL AND json_array_length(parts) >= $minimum
		ORDER BY (SELECT COALESCE(SUM(movie.popularity), 0) FROM movie
			WHERE movie.tmdb_id IN (SELECT value FROM json_each(collection.parts))) DESC, tmdb_id
		LIMIT $limit`, db.Vars{"minimum": minimumParts, "limit": limit})
}

// EnsureCollection returns a collection with its films stored, fetching them
// from TMDB when the list is missing or stale. The films come back in
// release order.
func (s *Service) EnsureCollection(ctx context.Context, collectionID int) (*Collection, []Title, error) {
	stored, err := s.Store.Collection(ctx, collectionID)
	if err != nil {
		return nil, nil, err
	}
	if stored == nil || isStale(stored.DetailFetchedAt, collectionDetailTTL) {
		stored, err = s.refreshCollection(ctx, collectionID, stored)
		if err != nil {
			return nil, nil, err
		}
	}
	titles, err := s.Store.Titles(ctx, MovieRefs(stored.PartIDs))
	if err != nil {
		return nil, nil, err
	}
	return stored, titles, nil
}

// refreshCollection fetches the collection once, even for concurrent calls.
// A failed refresh keeps serving the stored list.
func (s *Service) refreshCollection(ctx context.Context, collectionID int, stored *Collection) (*Collection, error) {
	key := fmt.Sprintf("collection:%d", collectionID)
	_, err, _ := s.inflight.Do(key, func() (any, error) {
		return nil, s.fetchCollection(context.WithoutCancel(ctx), collectionID)
	})
	if err != nil && stored != nil && stored.DetailFetchedAt != "" {
		return stored, nil
	}
	if err != nil {
		return nil, err
	}
	return s.Store.Collection(ctx, collectionID)
}

func (s *Service) fetchCollection(ctx context.Context, collectionID int) error {
	detail, err := s.tmdb.FetchCollection(ctx, collectionID)
	if err != nil {
		return err
	}
	if _, err := s.saveStubs(ctx, MediaTypeMovie, detail.Parts); err != nil {
		return err
	}
	collection := collectionFrom(detail)
	if err := s.Store.SaveCollection(ctx, collection); err != nil {
		return err
	}
	return s.Store.LinkMoviesToCollection(ctx, collection.TMDBID, collection.PartIDs)
}

func collectionFrom(detail *tmdb.CollectionDetail) Collection {
	return Collection{
		TMDBID: detail.ID, Name: detail.Name, Overview: detail.Overview,
		PosterPath: detail.PosterPath, BackdropPath: detail.BackdropPath,
		PartIDs: releaseOrder(detail.Parts),
	}
}

// releaseOrder returns the film ids by release date, oldest first. Films
// without a date come last; equal dates keep TMDB's order.
func releaseOrder(parts []tmdb.SearchResult) []int {
	ordered := slices.Clone(parts)
	slices.SortStableFunc(ordered, func(left, right tmdb.SearchResult) int {
		return compareReleaseDates(left.ReleaseDate, right.ReleaseDate)
	})
	ids := make([]int, 0, len(ordered))
	for _, part := range ordered {
		ids = append(ids, part.ID)
	}
	return ids
}

func compareReleaseDates(left, right string) int {
	if left == "" || right == "" {
		return cmp.Compare(boolRank(left == ""), boolRank(right == ""))
	}
	return cmp.Compare(left, right)
}

func boolRank(value bool) int {
	if value {
		return 1
	}
	return 0
}

// MovieRefs turns movie ids into refs.
func MovieRefs(ids []int) []MediaRef {
	refs := make([]MediaRef, 0, len(ids))
	for _, id := range ids {
		refs = append(refs, MediaRef{MediaType: MediaTypeMovie, MediaID: id})
	}
	return refs
}
