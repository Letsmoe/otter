-- How the hosts behind stream links (voe.sx, dood.to, ...) did lately, to
-- race the historically good ones first. Both values are moving averages so
-- a host that changes is noticed within a few attempts.
CREATE TABLE hoster_stats (
    host        TEXT PRIMARY KEY,
    reliability REAL NOT NULL,       -- 0..1: share of recent attempts that played
    speed_bps   REAL,                -- measured download speed, NULL until one played
    attempts    INTEGER NOT NULL,
    updated_at  TEXT NOT NULL
);
