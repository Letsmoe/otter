-- A rewatch is a second run through a movie or series with progress of its
-- own. While one is active, the title's progress reads and writes go to
-- rewatch_progress, so watch_history (Continue watching, recommendations,
-- the original "finished") stays as it was.
CREATE TABLE rewatch (
  profile_id  INTEGER NOT NULL,
  media_type  TEXT NOT NULL,
  media_id    INTEGER NOT NULL,
  started_at  TEXT NOT NULL,
  updated_at  TEXT NOT NULL,
  -- Empty while the run is going.
  finished_at TEXT NOT NULL DEFAULT '',
  PRIMARY KEY (profile_id, media_type, media_id)
);

CREATE TABLE rewatch_progress (
  profile_id       INTEGER NOT NULL,
  media_type       TEXT NOT NULL,
  media_id         INTEGER NOT NULL,
  progress_seconds REAL NOT NULL DEFAULT 0,
  duration_seconds REAL NOT NULL DEFAULT 0,
  completed        BOOLEAN NOT NULL DEFAULT 0,
  started_at       TEXT NOT NULL,
  updated_at       TEXT NOT NULL,
  PRIMARY KEY (profile_id, media_type, media_id)
);
