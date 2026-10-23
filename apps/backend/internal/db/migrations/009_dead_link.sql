-- Stream links a resolver reported as gone (the hoster deleted the video).
-- They are removed from stream and kept out when a plugin lists them again.
CREATE TABLE dead_link (
    url      TEXT PRIMARY KEY,
    found_at TEXT NOT NULL
);
