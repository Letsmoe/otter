-- A profile's Letterboxd account, whose public diary feed is read for new
-- watched films and ratings. Writing to Letterboxd needs API access nobody
-- here has, so this only ever reads.
CREATE TABLE letterboxd_sync (
  profile_id     INTEGER PRIMARY KEY,
  username       TEXT NOT NULL,
  last_synced_at TEXT NOT NULL DEFAULT '',
  -- Why the last sync failed; empty after a good one.
  last_error     TEXT NOT NULL DEFAULT '',
  -- What the last sync added.
  last_watched   INTEGER NOT NULL DEFAULT 0,
  last_rated     INTEGER NOT NULL DEFAULT 0
);
