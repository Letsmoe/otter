package library

import (
	"context"
	"encoding/json"

	"github.com/Letsmoe/otter/internal/db"
	"github.com/Letsmoe/otter/internal/plugins"
)

// StoredFiller is what the filler plugins answered for a series, and when.
type StoredFiller struct {
	FetchedAt string
	Entries   []plugins.FillerEpisode
}

type fillerRow struct {
	FetchedAt string `json:"fetched_at"`
	Entries   string `json:"entries"`
}

// Filler loads the plugins' answer for a series; nil when they were never asked.
func (s *Store) Filler(ctx context.Context, seriesID int) (*StoredFiller, error) {
	row, err := db.QueryOneAs[fillerRow](ctx, s.database, "SELECT fetched_at, entries FROM series_filler WHERE series_id = $series_id",
		db.Vars{"series_id": seriesID})
	if err != nil || row == nil {
		return nil, err
	}
	stored := &StoredFiller{FetchedAt: row.FetchedAt}
	if err := json.Unmarshal([]byte(row.Entries), &stored.Entries); err != nil {
		return nil, err
	}
	return stored, nil
}

// SaveFiller keeps the plugins' answer for a series (an empty one too, so they aren't asked again at once).
func (s *Store) SaveFiller(ctx context.Context, seriesID int, entries []plugins.FillerEpisode) error {
	encoded, err := json.Marshal(entries)
	if err != nil {
		return err
	}
	_, err = s.database.Exec(ctx, `INSERT INTO series_filler (series_id, fetched_at, entries)
		VALUES ($series_id, $now, $entries)
		ON CONFLICT (series_id) DO UPDATE SET fetched_at = excluded.fetched_at, entries = excluded.entries`,
		db.Vars{"series_id": seriesID, "now": db.Now(), "entries": string(encoded)})
	return err
}

// SetEpisodeFiller marks the series' episodes with their kinds (by TMDB episode id); the others lose their mark.
func (s *Store) SetEpisodeFiller(ctx context.Context, seriesID int, kinds map[int]string) error {
	return s.database.Tx(ctx, func(tx *db.DB) error {
		_, err := tx.Exec(ctx, "UPDATE episode SET filler = NULL WHERE series_id = $series_id", db.Vars{"series_id": seriesID})
		if err != nil {
			return err
		}
		for episodeID, kind := range kinds {
			_, err := tx.Exec(ctx, "UPDATE episode SET filler = $kind WHERE tmdb_id = $id",
				db.Vars{"kind": kind, "id": episodeID})
			if err != nil {
				return err
			}
		}
		return nil
	})
}

// SeriesEpisodes lists every stored episode of a series, specials included.
func (s *Store) SeriesEpisodes(ctx context.Context, seriesID int) ([]Episode, error) {
	return db.QueryAs[Episode](ctx, s.database, "SELECT * FROM episode WHERE series_id = $series_id",
		db.Vars{"series_id": seriesID})
}
