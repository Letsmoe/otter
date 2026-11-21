-- TMDB collections: the films of a franchise, shown as a watch-order guide.
-- parts holds the TMDB ids of the films in release order.
CREATE TABLE collection (
  tmdb_id           INTEGER PRIMARY KEY,
  name              TEXT NOT NULL,
  overview          TEXT,
  poster_path       TEXT,
  backdrop_path     TEXT,
  parts             JSON NOT NULL DEFAULT '[]',
  detail_fetched_at TEXT,
  created_at        TEXT NOT NULL
);

-- The collection a movie belongs to (0 or NULL: none).
ALTER TABLE movie ADD COLUMN collection_id INTEGER;
CREATE INDEX movie_collection ON movie (collection_id);
