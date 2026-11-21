-- Titles a profile hid with "Not for me" on a recommendation. The recommendation
-- engine never suggests them again and ranks similar titles lower.
CREATE TABLE dismissed_title (
  profile_id   INTEGER NOT NULL,
  media_type   TEXT NOT NULL,
  media_id     INTEGER NOT NULL,
  dismissed_at TEXT NOT NULL,
  PRIMARY KEY (profile_id, media_type, media_id)
);
