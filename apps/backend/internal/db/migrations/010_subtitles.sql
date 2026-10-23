-- Caption files subtitles plugins found for a movie or episode. media_id is
-- the API id ("movie:603", "episode:62085"); id is a hash of plugin and url,
-- stable across lookups, and names the file in /api/subtitles/<id>.vtt.
CREATE TABLE subtitle (
  id               TEXT PRIMARY KEY,
  media_id         TEXT NOT NULL,
  plugin           TEXT NOT NULL,
  url              TEXT NOT NULL,
  language         TEXT NOT NULL,
  format           TEXT NOT NULL,
  label            TEXT NOT NULL DEFAULT '',
  release          TEXT,
  hearing_impaired BOOLEAN NOT NULL DEFAULT 0,
  -- Request headers the download needs.
  headers          JSON,
  -- Place in the plugin's own order, best first.
  position         INTEGER NOT NULL DEFAULT 0,
  created_at       TEXT NOT NULL
);

CREATE INDEX subtitle_media ON subtitle (media_id);

-- Caption and sound preferences of a profile.
ALTER TABLE profile ADD COLUMN subtitles_enabled BOOLEAN NOT NULL DEFAULT 0;
-- ISO 639-1; NULL = the profile's first language.
ALTER TABLE profile ADD COLUMN subtitle_language TEXT;
ALTER TABLE profile ADD COLUMN subtitle_size TEXT NOT NULL DEFAULT 'medium';
ALTER TABLE profile ADD COLUMN voice_boost BOOLEAN NOT NULL DEFAULT 0;
