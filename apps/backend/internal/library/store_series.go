package library

import (
	"context"

	"github.com/neoworks/otter/internal/db"
)

// SaveSeasons upserts a series' season list (without episodes).
func (s *Store) SaveSeasons(ctx context.Context, seasons []Season) error {
	return s.database.Tx(ctx, func(tx *db.DB) error {
		for _, season := range seasons {
			_, err := tx.Exec(ctx, `INSERT INTO season (tmdb_id, series_id, season_number, name,
				overview, air_date, poster_path, episode_count)
			VALUES ($tmdb_id, $series_id, $season_number, $name, $overview, $air_date,
				$poster_path, $episode_count)
			ON CONFLICT (tmdb_id) DO UPDATE SET name = excluded.name,
				overview = excluded.overview, air_date = excluded.air_date,
				poster_path = excluded.poster_path, episode_count = excluded.episode_count`,
				db.Vars{
					"tmdb_id": season.TMDBID, "series_id": season.SeriesID,
					"season_number": season.SeasonNumber, "name": season.Name,
					"overview": season.Overview, "air_date": nullable(season.AirDate),
					"poster_path": nullable(season.PosterPath), "episode_count": season.EpisodeCount,
				})
			if err != nil {
				return err
			}
		}
		return nil
	})
}

// SaveEpisodes upserts one season's episodes and marks the season fetched.
func (s *Store) SaveEpisodes(ctx context.Context, seriesID, seasonNumber int, episodes []Episode) error {
	return s.database.Tx(ctx, func(tx *db.DB) error {
		for _, episode := range episodes {
			if err := upsertEpisode(ctx, tx, episode); err != nil {
				return err
			}
		}
		_, err := tx.Exec(ctx, `UPDATE season SET detail_fetched_at = $now
			WHERE series_id = $series_id AND season_number = $season_number`,
			db.Vars{"now": db.Now(), "series_id": seriesID, "season_number": seasonNumber})
		return err
	})
}

func upsertEpisode(ctx context.Context, tx *db.DB, episode Episode) error {
	_, err := tx.Exec(ctx, `INSERT INTO episode (tmdb_id, series_id, season_number,
		episode_number, name, overview, air_date, runtime, still_path, vote_average)
	VALUES ($tmdb_id, $series_id, $season_number, $episode_number, $name, $overview,
		$air_date, $runtime, $still_path, $vote_average)
	ON CONFLICT (tmdb_id) DO UPDATE SET name = excluded.name, overview = excluded.overview,
		air_date = excluded.air_date, runtime = excluded.runtime,
		still_path = excluded.still_path, vote_average = excluded.vote_average`,
		db.Vars{
			"tmdb_id": episode.TMDBID, "series_id": episode.SeriesID,
			"season_number": episode.SeasonNumber, "episode_number": episode.EpisodeNumber,
			"name": episode.Name, "overview": episode.Overview,
			"air_date": nullable(episode.AirDate), "runtime": episode.Runtime,
			"still_path": nullable(episode.StillPath), "vote_average": episode.VoteAverage,
		})
	return err
}

// Seasons lists a series' seasons in order, specials (season 0) last.
func (s *Store) Seasons(ctx context.Context, seriesID int) ([]Season, error) {
	return db.QueryAs[Season](ctx, s.database, `SELECT * FROM season WHERE series_id = $series_id
		ORDER BY season_number = 0, season_number`, db.Vars{"series_id": seriesID})
}

// Season loads one season; nil when it is not stored.
func (s *Store) Season(ctx context.Context, seriesID, seasonNumber int) (*Season, error) {
	return db.QueryOneAs[Season](ctx, s.database, `SELECT * FROM season
		WHERE series_id = $series_id AND season_number = $season_number`,
		db.Vars{"series_id": seriesID, "season_number": seasonNumber})
}

// Episodes lists one season's episodes in order.
func (s *Store) Episodes(ctx context.Context, seriesID, seasonNumber int) ([]Episode, error) {
	return db.QueryAs[Episode](ctx, s.database, `SELECT * FROM episode
		WHERE series_id = $series_id AND season_number = $season_number
		ORDER BY episode_number`, db.Vars{"series_id": seriesID, "season_number": seasonNumber})
}

// Episode loads one episode by its TMDB id; nil when it is not stored.
func (s *Store) Episode(ctx context.Context, tmdbID int) (*Episode, error) {
	return db.QueryOneAs[Episode](ctx, s.database, "SELECT * FROM episode WHERE tmdb_id = $id",
		db.Vars{"id": tmdbID})
}

// NextEpisode returns the episode after the given one, crossing into the
// next season; nil at the end of the series.
func (s *Store) NextEpisode(ctx context.Context, current Episode) (*Episode, error) {
	return db.QueryOneAs[Episode](ctx, s.database, `SELECT * FROM episode
		WHERE series_id = $series_id AND season_number > 0
		AND (season_number > $season_number
			OR (season_number = $season_number AND episode_number > $episode_number))
		ORDER BY season_number, episode_number LIMIT 1`,
		db.Vars{"series_id": current.SeriesID, "season_number": current.SeasonNumber,
			"episode_number": current.EpisodeNumber})
}

// SavePerson writes a fully fetched person.
func (s *Store) SavePerson(ctx context.Context, person Person) error {
	_, err := s.database.Exec(ctx, `INSERT INTO person (tmdb_id, imdb_id, name, profile_path,
		biography, birthday, deathday, place_of_birth, known_for_department, popularity,
		detail_fetched_at)
	VALUES ($tmdb_id, $imdb_id, $name, $profile_path, $biography, $birthday, $deathday,
		$place_of_birth, $known_for_department, $popularity, $now)
	ON CONFLICT (tmdb_id) DO UPDATE SET imdb_id = excluded.imdb_id, name = excluded.name,
		profile_path = excluded.profile_path, biography = excluded.biography,
		birthday = excluded.birthday, deathday = excluded.deathday,
		place_of_birth = excluded.place_of_birth,
		known_for_department = excluded.known_for_department,
		popularity = excluded.popularity, detail_fetched_at = excluded.detail_fetched_at`,
		db.Vars{
			"tmdb_id": person.TMDBID, "imdb_id": nullable(person.IMDBID), "name": person.Name,
			"profile_path": nullable(person.ProfilePath), "biography": person.Biography,
			"birthday": nullable(person.Birthday), "deathday": nullable(person.Deathday),
			"place_of_birth":       nullable(person.PlaceOfBirth),
			"known_for_department": person.KnownForDepartment, "popularity": person.Popularity,
			"now": db.Now(),
		})
	return err
}

// Person loads one person; nil when it is not stored.
func (s *Store) Person(ctx context.Context, tmdbID int) (*Person, error) {
	return db.QueryOneAs[Person](ctx, s.database, "SELECT * FROM person WHERE tmdb_id = $id",
		db.Vars{"id": tmdbID})
}

// AddCredit records one credit of a person without touching the title's
// other credits (used for filmographies).
func (s *Store) AddCredit(ctx context.Context, ref MediaRef, credit Credit) error {
	return s.database.Tx(ctx, func(tx *db.DB) error {
		return insertCredit(ctx, tx, ref, credit)
	})
}

// Filmography lists the titles a person is credited on, newest first, once
// each. A non-empty job (e.g. "Director") keeps only those crew credits.
func (s *Store) Filmography(ctx context.Context, personID int, job string) ([]Title, error) {
	refs, err := db.QueryAs[MediaRef](ctx, s.database, `SELECT credit.media_type, credit.media_id
		FROM credit
	LEFT JOIN movie ON credit.media_type = 'movie' AND movie.tmdb_id = credit.media_id
	LEFT JOIN series ON credit.media_type = 'series' AND series.tmdb_id = credit.media_id
	WHERE credit.person_id = $person_id AND ($job = '' OR credit.job = $job)
	GROUP BY credit.media_type, credit.media_id
	ORDER BY MAX(COALESCE(movie.release_date, series.first_air_date)) DESC`,
		db.Vars{"person_id": personID, "job": job})
	if err != nil {
		return nil, err
	}
	return s.Titles(ctx, refs)
}

// SeasonByID loads one season by its TMDB id; nil when it is not stored.
func (s *Store) SeasonByID(ctx context.Context, tmdbID int) (*Season, error) {
	return db.QueryOneAs[Season](ctx, s.database, "SELECT * FROM season WHERE tmdb_id = $id",
		db.Vars{"id": tmdbID})
}
