-- Simple mode: the Android app shows only Continue, My List and Search with
-- big text and big buttons, and plays with the best link.
ALTER TABLE profile ADD COLUMN simple_mode BOOLEAN NOT NULL DEFAULT 0;
