package library

import (
	"context"

	"github.com/neoworks/otter/internal/db"
	"github.com/neoworks/otter/internal/plugins"
)

// ReplacePluginStreams swaps the streams one plugin found for a title, so
// links a site dropped disappear while other plugins' links stay. A link's
// playable verdict survives the swap; dead links stay out.
func (s *Store) ReplacePluginStreams(ctx context.Context, ref MediaRef, pluginName string, downloads []plugins.Download) error {
	return s.database.Tx(ctx, func(tx *db.DB) error {
		dead, err := deadLinks(ctx, tx, downloads)
		if err != nil {
			return err
		}
		vars := db.Vars{"media_type": ref.MediaType, "media_id": ref.MediaID, "plugin": pluginName}
		kept := map[string]bool{}
		for _, download := range downloads {
			if dead[download.URL] {
				continue
			}
			kept[download.URL] = true
			if err := upsertStream(ctx, tx, ref, pluginName, download); err != nil {
				return err
			}
		}
		urls := make([]string, 0, len(kept))
		for url := range kept {
			urls = append(urls, url)
		}
		vars["urls"] = urls
		_, err = tx.Exec(ctx, `DELETE FROM stream WHERE media_type = $media_type
			AND media_id = $media_id AND plugin = $plugin
			AND url NOT IN (SELECT value FROM json_each($urls))`, vars)
		return err
	})
}

// deadLinks returns which of the downloads' URLs are known to be dead.
func deadLinks(ctx context.Context, tx *db.DB, downloads []plugins.Download) (map[string]bool, error) {
	urls := make([]string, 0, len(downloads))
	for _, download := range downloads {
		urls = append(urls, download.URL)
	}
	found, err := tx.Strings(ctx, `SELECT url FROM dead_link WHERE url IN (SELECT value FROM json_each($urls))`,
		db.Vars{"urls": urls})
	if err != nil {
		return nil, err
	}
	dead := make(map[string]bool, len(found))
	for _, url := range found {
		dead[url] = true
	}
	return dead, nil
}

// ForgetDeadLink removes a link whose video is gone from every title and
// keeps it from being stored again.
func (s *Store) ForgetDeadLink(ctx context.Context, url string) error {
	return s.database.Tx(ctx, func(tx *db.DB) error {
		vars := db.Vars{"url": url, "now": db.Now()}
		if _, err := tx.Exec(ctx, `INSERT INTO dead_link (url, found_at) VALUES ($url, $now)
			ON CONFLICT (url) DO NOTHING`, vars); err != nil {
			return err
		}
		_, err := tx.Exec(ctx, `DELETE FROM stream WHERE url = $url`, vars)
		return err
	})
}

func upsertStream(ctx context.Context, tx *db.DB, ref MediaRef, pluginName string, download plugins.Download) error {
	_, err := tx.Exec(ctx, `INSERT INTO stream (media_type, media_id, plugin, url, label,
		quality, language, format, size_bytes, speed_bps, found_at)
	VALUES ($media_type, $media_id, $plugin, $url, $label, $quality, $language, $format,
		$size_bytes, $speed_bps, $now)
	ON CONFLICT (media_type, media_id, url) DO UPDATE SET plugin = excluded.plugin,
		label = excluded.label, quality = COALESCE(excluded.quality, stream.quality), language = excluded.language,
		format = excluded.format, size_bytes = excluded.size_bytes,
		speed_bps = COALESCE(excluded.speed_bps, stream.speed_bps)`,
		db.Vars{
			"media_type": ref.MediaType, "media_id": ref.MediaID, "plugin": pluginName,
			"url": download.URL, "label": download.Label, "quality": nullable(download.Quality),
			"language": nullable(download.Language), "format": nullable(download.Format),
			"size_bytes": nullableNumber(float64(download.SizeBytes)),
			"speed_bps":  nullableNumber(download.SpeedBps), "now": db.Now(),
		})
	return err
}

// Streams lists a title's streams: known-playable first, then untried, then
// failed; faster before slower within each group.
func (s *Store) Streams(ctx context.Context, ref MediaRef) ([]Stream, error) {
	return db.QueryAs[Stream](ctx, s.database, `SELECT * FROM stream
	WHERE media_type = $media_type AND media_id = $media_id
	ORDER BY CASE playable WHEN 1 THEN 0 WHEN 0 THEN 2 ELSE 1 END,
		COALESCE(speed_bps, 0) DESC, id`,
		db.Vars{"media_type": ref.MediaType, "media_id": ref.MediaID})
}

// MarkPlayable records whether resolving a stream URL worked, and what a
// probe measured: speed and, when known, the resolution, which replaces what
// the site claimed.
func (s *Store) MarkPlayable(ctx context.Context, url string, playable bool, speedBps float64, quality string) error {
	_, err := s.database.Exec(ctx, `UPDATE stream SET playable = $playable,
		speed_bps = COALESCE($speed_bps, speed_bps), quality = COALESCE($quality, quality) WHERE url = $url`,
		db.Vars{"url": url, "playable": playable, "speed_bps": nullableNumber(speedBps), "quality": nullable(quality)})
	return err
}

// MarkStreamsFetched stamps when plugins were last asked for a title.
func (s *Store) MarkStreamsFetched(ctx context.Context, ref MediaRef) error {
	table := "movie"
	if ref.MediaType == MediaTypeEpisode {
		table = "episode"
	}
	_, err := s.database.Exec(ctx, "UPDATE "+table+" SET streams_fetched_at = $now WHERE tmdb_id = $id",
		db.Vars{"now": db.Now(), "id": ref.MediaID})
	return err
}

// ExpireAllStreams marks every title's stream lookup as due.
func (s *Store) ExpireAllStreams(ctx context.Context) error {
	for _, table := range []string{"movie", "episode"} {
		if _, err := s.database.Exec(ctx, "UPDATE "+table+" SET streams_fetched_at = NULL", nil); err != nil {
			return err
		}
	}
	return nil
}

// StreamsFetchedAt returns when plugins were last asked for a title, or "".
func (s *Store) StreamsFetchedAt(ctx context.Context, ref MediaRef) (string, error) {
	table := "movie"
	if ref.MediaType == MediaTypeEpisode {
		table = "episode"
	}
	values, err := s.database.Strings(ctx, "SELECT streams_fetched_at FROM "+table+" WHERE tmdb_id = $id",
		db.Vars{"id": ref.MediaID})
	if err != nil || len(values) == 0 {
		return "", err
	}
	return values[0], nil
}

func nullableNumber(value float64) any {
	if value == 0 {
		return nil
	}
	return value
}

// Stream loads one stream by id; nil when it does not exist.
func (s *Store) Stream(ctx context.Context, id int) (*Stream, error) {
	return db.QueryOneAs[Stream](ctx, s.database, "SELECT * FROM stream WHERE id = $id", db.Vars{"id": id})
}

// AddStream stores one link someone else found (Watch Together shares the
// link it plays) and returns it, ready to resolve.
func (s *Store) AddStream(ctx context.Context, ref MediaRef, pluginName string, download plugins.Download) (*Stream, error) {
	if err := upsertStream(ctx, s.database, ref, pluginName, download); err != nil {
		return nil, err
	}
	return db.QueryOneAs[Stream](ctx, s.database, `SELECT * FROM stream
	WHERE media_type = $media_type AND media_id = $media_id AND url = $url`,
		db.Vars{"media_type": ref.MediaType, "media_id": ref.MediaID, "url": download.URL})
}
