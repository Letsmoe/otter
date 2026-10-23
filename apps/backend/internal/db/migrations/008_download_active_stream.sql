-- The link a download is currently saving, so a restart resumes from it.
ALTER TABLE download ADD COLUMN active_stream_id INTEGER NOT NULL DEFAULT 0;
