-- The link the user picked for a download; 0 lets the worker choose.
ALTER TABLE download ADD COLUMN stream_id INTEGER NOT NULL DEFAULT 0;
