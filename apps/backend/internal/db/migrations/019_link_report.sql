-- What a link really delivered when it played, reported by the app: measured
-- resolution, bitrate, stalls and time to the first picture, next to what the
-- plugin's label claimed. One row per link (URL), so it outlives the stream
-- row a plugin replaces on every lookup. Links are ranked with it.
CREATE TABLE link_report (
  url                TEXT PRIMARY KEY,
  host               TEXT NOT NULL,
  -- Pixel heights; 0 when unknown. Measured counts wide pictures by their width.
  claimed_height     INTEGER NOT NULL DEFAULT 0,
  measured_height    INTEGER NOT NULL DEFAULT 0,
  -- The latest play.
  bitrate_bps        INTEGER NOT NULL DEFAULT 0,
  stall_count        INTEGER NOT NULL DEFAULT 0,
  stall_millis       INTEGER NOT NULL DEFAULT 0,
  first_frame_millis INTEGER NOT NULL DEFAULT 0,
  played_millis      INTEGER NOT NULL DEFAULT 0,
  -- Over all reported plays.
  plays              INTEGER NOT NULL DEFAULT 0,
  overclaims         INTEGER NOT NULL DEFAULT 0,
  rough_plays        INTEGER NOT NULL DEFAULT 0,
  updated_at         TEXT NOT NULL
);
CREATE INDEX link_report_host ON link_report (host);

-- What the plugin said, kept when a probe replaces `quality` with what it saw.
ALTER TABLE stream ADD COLUMN claimed_quality TEXT;
UPDATE stream SET claimed_quality = quality;
