-- Otter schema. Every title-like row is keyed by its TMDB id; rows that can
-- point at more than one kind of title use (media_type, media_id) where
-- media_type is 'movie', 'series' or 'episode'.
--
-- *_fetched_at columns are NULL until the matching TMDB/plugin fetch ran, so
-- stubs (titles only seen as "related") stay cheap until someone opens them.

-- ---------------------------------------------------------------------------
-- Catalog (from TMDB)

CREATE TABLE movie (
  tmdb_id            INTEGER PRIMARY KEY,
  imdb_id            TEXT,
  title              TEXT NOT NULL,
  original_title     TEXT,
  overview           TEXT,
  tagline            TEXT,
  release_date       TEXT,
  runtime            INTEGER,
  popularity         REAL NOT NULL DEFAULT 0,
  vote_average       REAL NOT NULL DEFAULT 0,
  vote_count         INTEGER NOT NULL DEFAULT 0,
  poster_path        TEXT,
  backdrop_path      TEXT,
  logo_path          TEXT,
  trailer_key        TEXT,
  genres             JSON NOT NULL DEFAULT '[]',
  keywords           JSON NOT NULL DEFAULT '[]',
  adult              BOOLEAN NOT NULL DEFAULT 0,
  detail_fetched_at  TEXT,
  streams_fetched_at TEXT,
  created_at         TEXT NOT NULL
);
CREATE INDEX movie_popularity ON movie (popularity DESC);

CREATE TABLE series (
  tmdb_id           INTEGER PRIMARY KEY,
  imdb_id           TEXT,
  title             TEXT NOT NULL,
  original_title    TEXT,
  overview          TEXT,
  tagline           TEXT,
  first_air_date    TEXT,
  status            TEXT,
  popularity        REAL NOT NULL DEFAULT 0,
  vote_average      REAL NOT NULL DEFAULT 0,
  vote_count        INTEGER NOT NULL DEFAULT 0,
  poster_path       TEXT,
  backdrop_path     TEXT,
  logo_path         TEXT,
  trailer_key       TEXT,
  genres            JSON NOT NULL DEFAULT '[]',
  keywords          JSON NOT NULL DEFAULT '[]',
  adult             BOOLEAN NOT NULL DEFAULT 0,
  detail_fetched_at TEXT,
  created_at        TEXT NOT NULL
);
CREATE INDEX series_popularity ON series (popularity DESC);

CREATE TABLE season (
  tmdb_id           INTEGER PRIMARY KEY,
  series_id         INTEGER NOT NULL,
  season_number     INTEGER NOT NULL,
  name              TEXT,
  overview          TEXT,
  air_date          TEXT,
  poster_path       TEXT,
  episode_count     INTEGER NOT NULL DEFAULT 0,
  detail_fetched_at TEXT,
  UNIQUE (series_id, season_number)
);

CREATE TABLE episode (
  tmdb_id            INTEGER PRIMARY KEY,
  series_id          INTEGER NOT NULL,
  season_number      INTEGER NOT NULL,
  episode_number     INTEGER NOT NULL,
  name               TEXT,
  overview           TEXT,
  air_date           TEXT,
  runtime            INTEGER,
  still_path         TEXT,
  vote_average       REAL NOT NULL DEFAULT 0,
  streams_fetched_at TEXT,
  UNIQUE (series_id, season_number, episode_number)
);

CREATE TABLE person (
  tmdb_id              INTEGER PRIMARY KEY,
  imdb_id              TEXT,
  name                 TEXT NOT NULL,
  profile_path         TEXT,
  biography            TEXT,
  birthday             TEXT,
  deathday             TEXT,
  place_of_birth       TEXT,
  known_for_department TEXT,
  popularity           REAL NOT NULL DEFAULT 0,
  detail_fetched_at    TEXT
);

-- credit links a person to a movie or series. role is 'cast' or 'crew'; job is
-- the crew job ('Director', …) or '' for cast.
CREATE TABLE credit (
  media_type   TEXT NOT NULL,
  media_id     INTEGER NOT NULL,
  person_id    INTEGER NOT NULL,
  role         TEXT NOT NULL,
  job          TEXT NOT NULL DEFAULT '',
  character    TEXT,
  credit_order INTEGER NOT NULL DEFAULT 0,
  PRIMARY KEY (media_type, media_id, person_id, role, job)
);
CREATE INDEX credit_person ON credit (person_id);

-- related is the "check related on TMDB" step: an edge from a title to a
-- title TMDB recommends for it, or one sharing a director/lead actor.
-- source is 'tmdb' or 'person'; rank is the position in TMDB's list.
CREATE TABLE related (
  media_type   TEXT NOT NULL,
  media_id     INTEGER NOT NULL,
  related_type TEXT NOT NULL,
  related_id   INTEGER NOT NULL,
  source       TEXT NOT NULL,
  rank         INTEGER NOT NULL DEFAULT 0,
  PRIMARY KEY (media_type, media_id, related_type, related_id, source)
);
CREATE INDEX related_target ON related (related_type, related_id);

-- ---------------------------------------------------------------------------
-- Streams (from plugins). media_type is 'movie' or 'episode'.

CREATE TABLE stream (
  id         INTEGER PRIMARY KEY AUTOINCREMENT,
  media_type TEXT NOT NULL,
  media_id   INTEGER NOT NULL,
  plugin     TEXT NOT NULL,
  url        TEXT NOT NULL,
  label      TEXT,
  quality    TEXT,
  language   TEXT,
  format     TEXT,
  size_bytes INTEGER,
  speed_bps  REAL,
  -- 1 after a successful resolve, 0 after a failed one, NULL untried.
  playable   BOOLEAN,
  found_at   TEXT NOT NULL,
  UNIQUE (media_type, media_id, url)
);

-- ---------------------------------------------------------------------------
-- People using the app

CREATE TABLE profile (
  id         INTEGER PRIMARY KEY AUTOINCREMENT,
  name       TEXT NOT NULL UNIQUE,
  avatar_url TEXT,
  pin_hash   TEXT,
  is_child   BOOLEAN NOT NULL DEFAULT 0,
  created_at TEXT NOT NULL
);

CREATE TABLE watch_history (
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
CREATE INDEX watch_history_recent ON watch_history (profile_id, updated_at DESC);

CREATE TABLE user_rating (
  profile_id INTEGER NOT NULL,
  media_type TEXT NOT NULL,
  media_id   INTEGER NOT NULL,
  rating     REAL NOT NULL,
  rated_at   TEXT NOT NULL,
  PRIMARY KEY (profile_id, media_type, media_id)
);

CREATE TABLE watchlist (
  profile_id INTEGER NOT NULL,
  media_type TEXT NOT NULL,
  media_id   INTEGER NOT NULL,
  added_at   TEXT NOT NULL,
  PRIMARY KEY (profile_id, media_type, media_id)
);

-- ---------------------------------------------------------------------------
-- Infrastructure

-- setting holds app settings as key -> JSON value.
CREATE TABLE setting (
  key   TEXT PRIMARY KEY,
  value JSON NOT NULL
);

-- kv is the expiring key/value cache (stream cache index, proxy sessions,
-- PIN lockouts). expires_at is unix milliseconds; NULL never expires.
CREATE TABLE kv (
  key        TEXT PRIMARY KEY,
  value      TEXT NOT NULL,
  expires_at INTEGER
);
CREATE INDEX kv_expires_at ON kv (expires_at);
