-- Pinned shelves: a profile's own Home rows ("Comfort shows", "Watch with Anna").
-- position orders the shelves of a profile and the titles of a shelf, from 0.
CREATE TABLE shelf (
  id         INTEGER PRIMARY KEY AUTOINCREMENT,
  profile_id INTEGER NOT NULL,
  name       TEXT NOT NULL,
  position   INTEGER NOT NULL,
  created_at TEXT NOT NULL
);
CREATE INDEX shelf_profile ON shelf (profile_id, position);

CREATE TABLE shelf_title (
  shelf_id   INTEGER NOT NULL,
  media_type TEXT NOT NULL,
  media_id   INTEGER NOT NULL,
  position   INTEGER NOT NULL,
  added_at   TEXT NOT NULL,
  PRIMARY KEY (shelf_id, media_type, media_id)
);
