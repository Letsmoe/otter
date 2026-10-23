-- Notifications per profile, produced by the notify watcher for titles on the
-- profile's watchlist. media_* is the movie or series; episode_id is 0 unless
-- the notification is about one episode.
CREATE TABLE notification (
  id         INTEGER PRIMARY KEY AUTOINCREMENT,
  profile_id INTEGER NOT NULL,
  -- available | new_episode | coming_soon
  kind       TEXT NOT NULL,
  media_type TEXT NOT NULL,
  media_id   INTEGER NOT NULL,
  episode_id INTEGER NOT NULL DEFAULT 0,
  heading    TEXT NOT NULL,
  body       TEXT NOT NULL DEFAULT '',
  created_at TEXT NOT NULL,
  read_at    TEXT,
  UNIQUE (profile_id, kind, media_type, media_id, episode_id)
);
CREATE INDEX notification_recent ON notification (profile_id, created_at DESC);
