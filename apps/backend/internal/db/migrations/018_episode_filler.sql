-- Which episodes of an anime are filler, from the filler plugins: 'filler', 'mixed' or 'canon'.
-- NULL when no list covers the episode.
ALTER TABLE episode ADD COLUMN filler TEXT;

-- What a filler plugin answered for a series (a JSON list of numbered kinds), kept until it is
-- mapped onto the episodes and asked again; no entries means the site doesn't know the series.
CREATE TABLE series_filler (
  series_id  INTEGER PRIMARY KEY,
  fetched_at TEXT NOT NULL,
  entries    TEXT NOT NULL DEFAULT '[]'
);
