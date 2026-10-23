-- Movies and episodes a profile downloaded. The bytes live in the stream
-- cache (blob store) and stay pinned while a row points at them.
CREATE TABLE download (
  id          INTEGER PRIMARY KEY AUTOINCREMENT,
  profile_id  INTEGER NOT NULL,
  -- 'movie' or 'episode'
  media_type  TEXT NOT NULL,
  media_id    INTEGER NOT NULL,
  -- The episode's series; 0 for movies.
  series_id   INTEGER NOT NULL DEFAULT 0,
  -- queued | downloading | done | failed
  status      TEXT NOT NULL,
  progress    REAL NOT NULL DEFAULT 0,
  size_bytes  INTEGER NOT NULL DEFAULT 0,
  file_url    TEXT NOT NULL DEFAULT '',
  mime_type   TEXT NOT NULL DEFAULT '',
  error       TEXT NOT NULL DEFAULT '',
  created_at  TEXT NOT NULL,
  finished_at TEXT,
  UNIQUE (profile_id, media_type, media_id)
);
