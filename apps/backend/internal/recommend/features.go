package recommend

import (
	"context"
	"fmt"
	"time"

	"github.com/neoworks/otter/internal/db"
	"github.com/neoworks/otter/internal/library"
)

// titleFeatures is what scoring and re-ranking know about one title.
type titleFeatures struct {
	title       string
	genres      []string
	keywords    []string
	cast        []int // lead cast person ids
	directors   []int // director/creator person ids
	voteAverage float64
	voteCount   int
	released    time.Time
	adult       bool
}

// people lists cast and directors together, for the per-person page caps.
func (features *titleFeatures) people() []int {
	return append(append([]int{}, features.cast...), features.directors...)
}

type featureRow struct {
	MediaType   library.MediaType `json:"media_type"`
	TMDBID      int               `json:"tmdb_id"`
	Title       string            `json:"title"`
	Genres      []string          `json:"genres"`
	Keywords    []string          `json:"keywords"`
	VoteAverage float64           `json:"vote_average"`
	VoteCount   int               `json:"vote_count"`
	ReleaseDate string            `json:"release_date"`
	Adult       bool              `json:"adult"`
}

const featureQuery = `SELECT 'movie' AS media_type, tmdb_id, title, genres, keywords,
	vote_average, vote_count, release_date, adult FROM movie
	WHERE tmdb_id IN (SELECT value FROM json_each($movies))
	UNION ALL
	SELECT 'series', tmdb_id, title, genres, keywords, vote_average, vote_count,
	first_air_date, adult FROM series
	WHERE tmdb_id IN (SELECT value FROM json_each($series))`

type creditRow struct {
	MediaType library.MediaType `json:"media_type"`
	MediaID   int               `json:"media_id"`
	PersonID  int               `json:"person_id"`
	Role      string            `json:"role"`
}

// creditQuery reads the lead cast and the directors/creators of titles.
var creditQuery = fmt.Sprintf(`SELECT media_type, media_id, person_id, role FROM credit
	WHERE ((media_type = 'movie' AND media_id IN (SELECT value FROM json_each($movies)))
		OR (media_type = 'series' AND media_id IN (SELECT value FROM json_each($series))))
	AND ((role = 'cast' AND credit_order < %d) OR job IN ('Director', 'Creator'))`, leadCastSize)

// loadFeatures reads the features of the given titles.
func (e *Engine) loadFeatures(ctx context.Context, refs []library.MediaRef) (map[library.MediaRef]*titleFeatures, error) {
	vars := db.Vars{"movies": idsOf(refs, library.MediaTypeMovie), "series": idsOf(refs, library.MediaTypeSeries)}
	rows, err := db.QueryAs[featureRow](ctx, e.database, featureQuery, vars)
	if err != nil {
		return nil, fmt.Errorf("read features: %w", err)
	}
	features := make(map[library.MediaRef]*titleFeatures, len(rows))
	for _, row := range rows {
		released, _ := time.Parse("2006-01-02", row.ReleaseDate)
		features[library.MediaRef{MediaType: row.MediaType, MediaID: row.TMDBID}] = &titleFeatures{
			title: row.Title, genres: row.Genres, keywords: row.Keywords,
			voteAverage: row.VoteAverage, voteCount: row.VoteCount, released: released, adult: row.Adult,
		}
	}
	return features, e.attachPeople(ctx, vars, features)
}

func (e *Engine) attachPeople(ctx context.Context, vars db.Vars, features map[library.MediaRef]*titleFeatures) error {
	credits, err := db.QueryAs[creditRow](ctx, e.database, creditQuery, vars)
	if err != nil {
		return fmt.Errorf("read credits: %w", err)
	}
	for _, credit := range credits {
		title, found := features[library.MediaRef{MediaType: credit.MediaType, MediaID: credit.MediaID}]
		if !found {
			continue
		}
		if credit.Role == library.RoleCast {
			title.cast = append(title.cast, credit.PersonID)
			continue
		}
		title.directors = append(title.directors, credit.PersonID)
	}
	return nil
}

func idsOf(refs []library.MediaRef, mediaType library.MediaType) []int {
	ids := []int{}
	for _, ref := range refs {
		if ref.MediaType == mediaType {
			ids = append(ids, ref.MediaID)
		}
	}
	return ids
}
