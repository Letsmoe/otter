package subtitles

import (
	"context"
	"crypto/sha256"
	"encoding/hex"

	"github.com/neoworks/otter/internal/db"
	"github.com/neoworks/otter/internal/plugins"
)

// Subtitle is one caption file a plugin found for a movie or episode. After
// Rank, Label is the display label instead of the plugin's own.
type Subtitle struct {
	ID              string            `json:"id"`
	MediaID         string            `json:"media_id"`
	Plugin          string            `json:"plugin"`
	URL             string            `json:"url"`
	Language        string            `json:"language"`
	Format          string            `json:"format"`
	Label           string            `json:"label"`
	Release         string            `json:"release"`
	HearingImpaired bool              `json:"hearing_impaired"`
	Headers         map[string]string `json:"headers"`
	Position        int               `json:"position"`
	CreatedAt       string            `json:"created_at"`
}

// Store keeps the caption files plugins found.
type Store struct {
	database *db.DB
}

func NewStore(database *db.DB) *Store {
	return &Store{database: database}
}

// SubtitleID names a file in URLs: a hash of its media, plugin and link.
func SubtitleID(mediaID, pluginName, fileURL string) string {
	sum := sha256.Sum256([]byte(mediaID + "\n" + pluginName + "\n" + fileURL))
	return hex.EncodeToString(sum[:16])
}

// ReplacePluginFiles swaps the files one plugin found for a title, so files
// a source dropped disappear while other plugins' files stay.
func (s *Store) ReplacePluginFiles(ctx context.Context, mediaID, pluginName string, files []plugins.SubtitleFile) error {
	return s.database.Tx(ctx, func(tx *db.DB) error {
		keptIDs := make([]string, 0, len(files))
		for position, file := range files {
			subtitle := subtitleOf(mediaID, pluginName, position, file)
			keptIDs = append(keptIDs, subtitle.ID)
			if err := upsertSubtitle(ctx, tx, subtitle); err != nil {
				return err
			}
		}
		_, err := tx.Exec(ctx, `DELETE FROM subtitle WHERE media_id = $media_id AND plugin = $plugin
			AND id NOT IN (SELECT value FROM json_each($ids))`,
			db.Vars{"media_id": mediaID, "plugin": pluginName, "ids": keptIDs})
		return err
	})
}

func subtitleOf(mediaID, pluginName string, position int, file plugins.SubtitleFile) Subtitle {
	return Subtitle{
		ID: SubtitleID(mediaID, pluginName, file.URL), MediaID: mediaID, Plugin: pluginName,
		URL: file.URL, Language: file.Language, Format: file.Format, Label: file.Label,
		Release: file.Release, HearingImpaired: file.HearingImpaired, Headers: file.Headers,
		Position: position,
	}
}

func upsertSubtitle(ctx context.Context, tx *db.DB, subtitle Subtitle) error {
	_, err := tx.Exec(ctx, `INSERT INTO subtitle (id, media_id, plugin, url, language, format, label,
		release, hearing_impaired, headers, position, created_at)
	VALUES ($id, $media_id, $plugin, $url, $language, $format, $label, $release, $hearing_impaired,
		$headers, $position, $now)
	ON CONFLICT (id) DO UPDATE SET language = excluded.language, format = excluded.format,
		label = excluded.label, release = excluded.release, hearing_impaired = excluded.hearing_impaired,
		headers = excluded.headers, position = excluded.position`,
		db.Vars{
			"id": subtitle.ID, "media_id": subtitle.MediaID, "plugin": subtitle.Plugin, "url": subtitle.URL,
			"language": subtitle.Language, "format": subtitle.Format, "label": subtitle.Label,
			"release": emptyAsNull(subtitle.Release), "hearing_impaired": subtitle.HearingImpaired,
			"headers": subtitle.Headers, "position": subtitle.Position, "now": db.Now(),
		})
	return err
}

func emptyAsNull(value string) any {
	if value == "" {
		return nil
	}
	return value
}

// ForMedia lists every stored file of a title, unranked.
func (s *Store) ForMedia(ctx context.Context, mediaID string) ([]Subtitle, error) {
	return db.QueryAs[Subtitle](ctx, s.database, "SELECT * FROM subtitle WHERE media_id = $media_id",
		db.Vars{"media_id": mediaID})
}

// Get loads one file by id; nil when it does not exist.
func (s *Store) Get(ctx context.Context, id string) (*Subtitle, error) {
	return db.QueryOneAs[Subtitle](ctx, s.database, "SELECT * FROM subtitle WHERE id = $id", db.Vars{"id": id})
}
