-- The "Notify me" bell of a title: overrides whether a profile gets news
-- about it. Without a row, titles on My List notify and others don't;
-- following = 1 follows a title that isn't on My List, 0 mutes one that is.
-- Adding or removing the title from My List deletes the row again.
CREATE TABLE notification_follow (
    profile_id  INTEGER NOT NULL,
    media_type  TEXT NOT NULL,
    media_id    INTEGER NOT NULL,
    following   INTEGER NOT NULL,
    followed_at TEXT NOT NULL,
    PRIMARY KEY (profile_id, media_type, media_id)
);
