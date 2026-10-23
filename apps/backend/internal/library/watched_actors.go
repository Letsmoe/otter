package library

import (
	"context"

	"github.com/neoworks/otter/internal/db"
)

// How many recently watched titles feed the actor row, and how far down the
// billing an actor still counts as a lead.
const (
	watchedActorTitles = 12
	watchedActorLeads  = 5
)

// WatchedActors lists the lead actors of the profile's most recently watched
// titles of one type (episodes count for their series): actors in more of
// those titles first, then the most recently watched. Actors without a
// portrait are skipped, since the row shows faces.
func (s *Service) WatchedActors(ctx context.Context, profileID int, mediaType MediaType, limit int) ([]Person, error) {
	return db.QueryAs[Person](ctx, s.Store.database, `WITH watched AS (
			SELECT
				CASE WHEN history.media_type = 'episode' THEN 'series' ELSE history.media_type END AS media_type,
				CASE WHEN history.media_type = 'episode' THEN episode.series_id ELSE history.media_id END AS media_id,
				MAX(history.updated_at) AS watched_at
			FROM watch_history AS history
			LEFT JOIN episode ON history.media_type = 'episode' AND episode.tmdb_id = history.media_id
			WHERE history.profile_id = $profile_id
			GROUP BY 1, 2
		),
		recent AS (
			SELECT * FROM watched WHERE media_type = $media_type
			ORDER BY watched_at DESC LIMIT $titles
		)
		SELECT person.tmdb_id, person.name, person.profile_path, person.popularity
		FROM recent
		JOIN credit ON credit.media_type = recent.media_type AND credit.media_id = recent.media_id
			AND credit.role = 'cast' AND credit.credit_order < $leads
		JOIN person ON person.tmdb_id = credit.person_id
		WHERE person.profile_path IS NOT NULL AND person.profile_path != ''
		GROUP BY person.tmdb_id
		ORDER BY COUNT(*) DESC, MAX(recent.watched_at) DESC, MIN(credit.credit_order) ASC
		LIMIT $limit`,
		db.Vars{
			"profile_id": profileID,
			"media_type": string(mediaType),
			"titles":     watchedActorTitles,
			"leads":      watchedActorLeads,
			"limit":      limit,
		})
}
