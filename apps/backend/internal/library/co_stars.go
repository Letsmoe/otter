package library

import (
	"context"

	"github.com/neoworks/otter/internal/db"
	"golang.org/x/sync/errgroup"
)

// A person's best-known roles are scanned for co-stars; only the top of each
// cast list counts, so a cameo doesn't make someone a frequent partner.
const (
	coStarTitles       = 15
	coStarBilling      = 10
	coStarFetchWorkers = 4
)

// CoStar is someone who acted alongside a person, and in how many titles.
type CoStar struct {
	Person       Person
	SharedTitles int
}

type coStarRow struct {
	TMDBID       int     `json:"tmdb_id"`
	Name         string  `json:"name"`
	ProfilePath  string  `json:"profile_path"`
	Popularity   float64 `json:"popularity"`
	SharedTitles int     `json:"shared_titles"`
}

// CoStars lists the actors who appear most often with personID across their
// best-known acting roles. Titles stored only as list stubs have no credits
// yet, so their details are fetched first (once; later calls read the store).
func (s *Service) CoStars(ctx context.Context, personID int, limit int) ([]CoStar, error) {
	refs, err := s.Store.leadingRoles(ctx, personID, coStarTitles)
	if err != nil {
		return nil, err
	}
	if err := s.fetchMissingDetails(ctx, refs); err != nil {
		return nil, err
	}
	rows, err := s.Store.coStarRows(ctx, personID, coStarTitles, limit)
	if err != nil {
		return nil, err
	}
	coStars := make([]CoStar, 0, len(rows))
	for _, row := range rows {
		person := Person{TMDBID: row.TMDBID, Name: row.Name, ProfilePath: row.ProfilePath, Popularity: row.Popularity}
		coStars = append(coStars, CoStar{Person: person, SharedTitles: row.SharedTitles})
	}
	return coStars, nil
}

// fetchMissingDetails loads full details (with credits) for stub titles,
// without the hooks and stream lookups a page visit triggers.
func (s *Service) fetchMissingDetails(ctx context.Context, refs []MediaRef) error {
	titles, err := s.Store.Titles(ctx, refs)
	if err != nil {
		return err
	}
	group, groupContext := errgroup.WithContext(ctx)
	group.SetLimit(coStarFetchWorkers)
	for _, title := range titles {
		if title.DetailFetchedAt != "" {
			continue
		}
		ref := MediaRef{MediaType: title.MediaType, MediaID: title.TMDBID}
		group.Go(func() error {
			// A title TMDB can't deliver just doesn't add co-stars.
			_ = s.fetchTitleOnce(groupContext, ref, false)
			return nil
		})
	}
	return group.Wait()
}

// leadingRoles lists a person's acting credits near the top of the bill,
// most popular title first.
func (s *Store) leadingRoles(ctx context.Context, personID int, limit int) ([]MediaRef, error) {
	return db.QueryAs[MediaRef](ctx, s.database, `SELECT credit.media_type, credit.media_id
		FROM credit
		LEFT JOIN movie ON credit.media_type = 'movie' AND movie.tmdb_id = credit.media_id
		LEFT JOIN series ON credit.media_type = 'series' AND series.tmdb_id = credit.media_id
		WHERE credit.person_id = $person_id AND credit.role = 'cast' AND credit.credit_order < $billing
		GROUP BY credit.media_type, credit.media_id
		ORDER BY MAX(COALESCE(movie.popularity, series.popularity, 0)) DESC
		LIMIT $limit`,
		db.Vars{"person_id": personID, "billing": coStarBilling, "limit": limit})
}

func (s *Store) coStarRows(ctx context.Context, personID int, titleLimit int, limit int) ([]coStarRow, error) {
	return db.QueryAs[coStarRow](ctx, s.database, `WITH roles AS (
			SELECT credit.media_type, credit.media_id
			FROM credit
			LEFT JOIN movie ON credit.media_type = 'movie' AND movie.tmdb_id = credit.media_id
			LEFT JOIN series ON credit.media_type = 'series' AND series.tmdb_id = credit.media_id
			WHERE credit.person_id = $person_id AND credit.role = 'cast' AND credit.credit_order < $billing
			GROUP BY credit.media_type, credit.media_id
			ORDER BY MAX(COALESCE(movie.popularity, series.popularity, 0)) DESC
			LIMIT $titles
		)
		SELECT person.tmdb_id, person.name, person.profile_path, person.popularity,
			COUNT(DISTINCT other.media_type || ':' || other.media_id) AS shared_titles
		FROM roles
		JOIN credit AS other ON other.media_type = roles.media_type AND other.media_id = roles.media_id
			AND other.role = 'cast' AND other.credit_order < $billing AND other.person_id != $person_id
		JOIN person ON person.tmdb_id = other.person_id
		WHERE person.profile_path IS NOT NULL AND person.profile_path != ''
		GROUP BY person.tmdb_id
		ORDER BY shared_titles DESC, person.popularity DESC
		LIMIT $limit`,
		db.Vars{"person_id": personID, "billing": coStarBilling, "titles": titleLimit, "limit": limit})
}
