package library

import (
	"context"
	"fmt"
	"strconv"

	"github.com/neoworks/otter/internal/db"
)

// Store is the SQL side of the library: catalog rows, credits, related edges
// and streams.
type Store struct {
	database *db.DB
}

func NewStore(database *db.DB) *Store {
	return &Store{database: database}
}

const movieColumns = `'movie' AS media_type, tmdb_id, imdb_id, title, original_title, overview,
	tagline, release_date, runtime, popularity, vote_average, vote_count, poster_path,
	backdrop_path, logo_path, trailer_key, genres, keywords, adult, detail_fetched_at`

const seriesColumns = `'series' AS media_type, tmdb_id, imdb_id, title, original_title, overview,
	tagline, first_air_date AS release_date, status, popularity, vote_average, vote_count,
	poster_path, backdrop_path, logo_path, trailer_key, genres, keywords, adult, detail_fetched_at`

// titleSelect returns the SELECT head for a title table.
func titleSelect(mediaType MediaType) (string, error) {
	switch mediaType {
	case MediaTypeMovie:
		return "SELECT " + movieColumns + " FROM movie", nil
	case MediaTypeSeries:
		return "SELECT " + seriesColumns + " FROM series", nil
	}
	return "", fmt.Errorf("not a title type: %q", mediaType)
}

// Title loads one movie or series; nil when it is not stored.
func (s *Store) Title(ctx context.Context, ref MediaRef) (*Title, error) {
	head, err := titleSelect(ref.MediaType)
	if err != nil {
		return nil, err
	}
	return db.QueryOneAs[Title](ctx, s.database, head+" WHERE tmdb_id = $id", db.Vars{"id": ref.MediaID})
}

// Titles loads several titles, keeping the order of refs and skipping
// the ones that are not stored.
func (s *Store) Titles(ctx context.Context, refs []MediaRef) ([]Title, error) {
	found := map[MediaRef]Title{}
	for _, mediaType := range []MediaType{MediaTypeMovie, MediaTypeSeries} {
		ids := idsOfType(refs, mediaType)
		if len(ids) == 0 {
			continue
		}
		head, _ := titleSelect(mediaType)
		rows, err := db.QueryAs[Title](ctx, s.database,
			head+" WHERE tmdb_id IN (SELECT value FROM json_each($ids))", db.Vars{"ids": ids})
		if err != nil {
			return nil, err
		}
		for _, row := range rows {
			found[MediaRef{MediaType: mediaType, MediaID: row.TMDBID}] = row
		}
	}
	titles := make([]Title, 0, len(found))
	for _, ref := range refs {
		if title, ok := found[ref]; ok {
			titles = append(titles, title)
		}
	}
	return titles, nil
}

// ListTitles pages through stored titles of one type, most popular first.
// An empty genre matches everything.
func (s *Store) ListTitles(ctx context.Context, mediaType MediaType, genre string, limit, offset int) ([]Title, error) {
	head, err := titleSelect(mediaType)
	if err != nil {
		return nil, err
	}
	query := head + ` WHERE adult = 0
		AND ($genre = '' OR EXISTS (SELECT 1 FROM json_each(genres) WHERE value = $genre))
		ORDER BY popularity DESC LIMIT $limit OFFSET $offset`
	return db.QueryAs[Title](ctx, s.database, query,
		db.Vars{"genre": genre, "limit": limit, "offset": offset})
}

// Genres lists every genre name in use, alphabetically.
func (s *Store) Genres(ctx context.Context) ([]string, error) {
	return s.database.Strings(ctx, `SELECT DISTINCT value FROM (
		SELECT genre.value FROM movie, json_each(movie.genres) AS genre
		UNION SELECT genre.value FROM series, json_each(series.genres) AS genre
	) ORDER BY value`, nil)
}

// SaveMovie writes a fully fetched movie.
func (s *Store) SaveMovie(ctx context.Context, movie Title) error {
	_, err := s.database.Exec(ctx, `INSERT INTO movie (tmdb_id, imdb_id, title, original_title,
		overview, tagline, release_date, runtime, popularity, vote_average, vote_count,
		poster_path, backdrop_path, logo_path, trailer_key, genres, keywords, adult,
		detail_fetched_at, created_at)
	VALUES ($tmdb_id, $imdb_id, $title, $original_title, $overview, $tagline, $release_date,
		$runtime, $popularity, $vote_average, $vote_count, $poster_path, $backdrop_path,
		$logo_path, $trailer_key, $genres, $keywords, $adult, $now, $now)
	ON CONFLICT (tmdb_id) DO UPDATE SET imdb_id = excluded.imdb_id, title = excluded.title,
		original_title = excluded.original_title, overview = excluded.overview,
		tagline = excluded.tagline, release_date = excluded.release_date,
		runtime = excluded.runtime, popularity = excluded.popularity,
		vote_average = excluded.vote_average, vote_count = excluded.vote_count,
		poster_path = excluded.poster_path, backdrop_path = excluded.backdrop_path,
		logo_path = excluded.logo_path, trailer_key = excluded.trailer_key,
		genres = excluded.genres, keywords = excluded.keywords, adult = excluded.adult,
		detail_fetched_at = excluded.detail_fetched_at`, titleVars(movie))
	return err
}

// SaveSeries writes a fully fetched series.
func (s *Store) SaveSeries(ctx context.Context, series Title) error {
	_, err := s.database.Exec(ctx, `INSERT INTO series (tmdb_id, imdb_id, title, original_title,
		overview, tagline, first_air_date, status, popularity, vote_average, vote_count,
		poster_path, backdrop_path, logo_path, trailer_key, genres, keywords, adult,
		detail_fetched_at, created_at)
	VALUES ($tmdb_id, $imdb_id, $title, $original_title, $overview, $tagline, $release_date,
		$status, $popularity, $vote_average, $vote_count, $poster_path, $backdrop_path,
		$logo_path, $trailer_key, $genres, $keywords, $adult, $now, $now)
	ON CONFLICT (tmdb_id) DO UPDATE SET imdb_id = excluded.imdb_id, title = excluded.title,
		original_title = excluded.original_title, overview = excluded.overview,
		tagline = excluded.tagline, first_air_date = excluded.first_air_date,
		status = excluded.status, popularity = excluded.popularity,
		vote_average = excluded.vote_average, vote_count = excluded.vote_count,
		poster_path = excluded.poster_path, backdrop_path = excluded.backdrop_path,
		logo_path = excluded.logo_path, trailer_key = excluded.trailer_key,
		genres = excluded.genres, keywords = excluded.keywords, adult = excluded.adult,
		detail_fetched_at = excluded.detail_fetched_at`, titleVars(series))
	return err
}

func titleVars(title Title) db.Vars {
	return db.Vars{
		"tmdb_id": title.TMDBID, "imdb_id": nullable(title.IMDBID), "title": title.Title,
		"original_title": title.OriginalTitle, "overview": title.Overview,
		"tagline": title.Tagline, "release_date": nullable(title.ReleaseDate),
		"runtime": title.Runtime, "status": title.Status, "popularity": title.Popularity,
		"vote_average": title.VoteAverage, "vote_count": title.VoteCount,
		"poster_path": nullable(title.PosterPath), "backdrop_path": nullable(title.BackdropPath),
		"logo_path": nullable(title.LogoPath), "trailer_key": nullable(title.TrailerKey),
		"genres": title.Genres, "keywords": title.Keywords, "adult": title.Adult,
		"now": db.Now(),
	}
}

// SaveStub records a title only seen in a list (search, related,
// filmography). It refreshes list-level fields but never overwrites a title
// whose detail was already fetched.
func (s *Store) SaveStub(ctx context.Context, stub Title) error {
	table, dateColumn := "movie", "release_date"
	if stub.MediaType == MediaTypeSeries {
		table, dateColumn = "series", "first_air_date"
	}
	query := fmt.Sprintf(`INSERT INTO %[1]s (tmdb_id, title, overview, %[2]s, popularity,
		vote_average, vote_count, poster_path, backdrop_path, genres, adult, created_at)
	VALUES ($tmdb_id, $title, $overview, $release_date, $popularity, $vote_average,
		$vote_count, $poster_path, $backdrop_path, $genres, $adult, $now)
	ON CONFLICT (tmdb_id) DO UPDATE SET popularity = excluded.popularity,
		vote_average = excluded.vote_average, vote_count = excluded.vote_count
	WHERE %[1]s.detail_fetched_at IS NULL`, table, dateColumn)
	_, err := s.database.Exec(ctx, query, titleVars(stub))
	return err
}

// ReplaceCredits swaps a title's credits and upserts the credited people.
func (s *Store) ReplaceCredits(ctx context.Context, ref MediaRef, credits []Credit) error {
	return s.database.Tx(ctx, func(tx *db.DB) error {
		refVars := db.Vars{"media_type": ref.MediaType, "media_id": ref.MediaID}
		if _, err := tx.Exec(ctx, "DELETE FROM credit WHERE media_type = $media_type AND media_id = $media_id", refVars); err != nil {
			return err
		}
		for _, credit := range credits {
			if err := insertCredit(ctx, tx, ref, credit); err != nil {
				return err
			}
		}
		return nil
	})
}

func insertCredit(ctx context.Context, tx *db.DB, ref MediaRef, credit Credit) error {
	vars := db.Vars{
		"media_type": ref.MediaType, "media_id": ref.MediaID, "person_id": credit.PersonID,
		"role": credit.Role, "job": credit.Job, "character": nullable(credit.Character),
		"credit_order": credit.Order, "name": credit.Name,
		"profile_path": nullable(credit.ProfilePath),
	}
	if _, err := tx.Exec(ctx, `INSERT INTO person (tmdb_id, name, profile_path)
		VALUES ($person_id, $name, $profile_path)
		ON CONFLICT (tmdb_id) DO UPDATE SET profile_path = COALESCE(excluded.profile_path, person.profile_path)`, vars); err != nil {
		return err
	}
	_, err := tx.Exec(ctx, `INSERT OR IGNORE INTO credit (media_type, media_id, person_id, role,
		job, character, credit_order)
		VALUES ($media_type, $media_id, $person_id, $role, $job, $character, $credit_order)`, vars)
	return err
}

// Credits lists a title's credits, cast by billing order, then crew.
func (s *Store) Credits(ctx context.Context, ref MediaRef) ([]Credit, error) {
	return db.QueryAs[Credit](ctx, s.database, `SELECT credit.media_type, credit.media_id,
		credit.person_id, credit.role, credit.job, credit.character, credit.credit_order,
		person.name, person.profile_path
	FROM credit JOIN person ON person.tmdb_id = credit.person_id
	WHERE credit.media_type = $media_type AND credit.media_id = $media_id
	ORDER BY credit.role, credit.credit_order`,
		db.Vars{"media_type": ref.MediaType, "media_id": ref.MediaID})
}

// ReplaceRelated swaps the related edges of one source for a title.
func (s *Store) ReplaceRelated(ctx context.Context, from MediaRef, source string, related []MediaRef) error {
	return s.database.Tx(ctx, func(tx *db.DB) error {
		vars := db.Vars{"media_type": from.MediaType, "media_id": from.MediaID, "source": source}
		if _, err := tx.Exec(ctx, `DELETE FROM related WHERE media_type = $media_type
			AND media_id = $media_id AND source = $source`, vars); err != nil {
			return err
		}
		for rank, target := range related {
			vars["related_type"] = target.MediaType
			vars["related_id"] = target.MediaID
			vars["rank"] = rank
			if _, err := tx.Exec(ctx, `INSERT OR IGNORE INTO related (media_type, media_id,
				related_type, related_id, source, rank)
				VALUES ($media_type, $media_id, $related_type, $related_id, $source, $rank)`, vars); err != nil {
				return err
			}
		}
		return nil
	})
}

// Related lists a title's related titles, TMDB's own picks first.
func (s *Store) Related(ctx context.Context, ref MediaRef, limit int) ([]Title, error) {
	rows, err := db.QueryAs[MediaRef](ctx, s.database, `SELECT related_type AS media_type,
		related_id AS media_id FROM related
	WHERE media_type = $media_type AND media_id = $media_id
	GROUP BY related_type, related_id
	ORDER BY MIN(CASE source WHEN 'tmdb' THEN 0 ELSE 1 END), MIN(rank) LIMIT $limit`,
		db.Vars{"media_type": ref.MediaType, "media_id": ref.MediaID, "limit": limit})
	if err != nil {
		return nil, err
	}
	return s.Titles(ctx, rows)
}

func idsOfType(refs []MediaRef, mediaType MediaType) []int {
	ids := []int{}
	for _, ref := range refs {
		if ref.MediaType == mediaType {
			ids = append(ids, ref.MediaID)
		}
	}
	return ids
}

// nullable stores empty strings as NULL so COALESCE and IS NULL work.
func nullable(value string) any {
	if value == "" {
		return nil
	}
	return value
}

func yearOf(date string) int {
	if len(date) < 4 {
		return 0
	}
	year, err := strconv.Atoi(date[:4])
	if err != nil {
		return 0
	}
	return year
}
