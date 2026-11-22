-- A title's YouTube videos (trailers, teasers, clips) and scene stills
-- (backdrops without text), for the apps' "Trailers & Scenes" row.
ALTER TABLE movie ADD COLUMN videos JSON NOT NULL DEFAULT '[]';
ALTER TABLE movie ADD COLUMN scene_paths JSON NOT NULL DEFAULT '[]';
ALTER TABLE series ADD COLUMN videos JSON NOT NULL DEFAULT '[]';
ALTER TABLE series ADD COLUMN scene_paths JSON NOT NULL DEFAULT '[]';

-- Fetched details lack both; mark them due like a language change does, so
-- titles pick them up the next time they're opened.
UPDATE movie SET detail_fetched_at = '1970-01-01T00:00:00.000Z' WHERE detail_fetched_at IS NOT NULL;
UPDATE series SET detail_fetched_at = '1970-01-01T00:00:00.000Z' WHERE detail_fetched_at IS NOT NULL;
