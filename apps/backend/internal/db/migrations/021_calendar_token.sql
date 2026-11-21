-- The secret in a profile's calendar feed URL. Empty until the feed is first asked for.
ALTER TABLE profile ADD COLUMN calendar_token TEXT NOT NULL DEFAULT '';
