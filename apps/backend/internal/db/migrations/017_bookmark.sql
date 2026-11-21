-- Moments a profile bookmarked while watching: the position and the caption line spoken then.
CREATE TABLE bookmark (
  id               INTEGER PRIMARY KEY AUTOINCREMENT,
  profile_id       INTEGER NOT NULL,
  media_type       TEXT NOT NULL,
  media_id         INTEGER NOT NULL,
  position_seconds REAL NOT NULL,
  caption          TEXT NOT NULL DEFAULT '',
  created_at       TEXT NOT NULL
);
CREATE INDEX bookmark_media ON bookmark (profile_id, media_type, media_id, position_seconds);
