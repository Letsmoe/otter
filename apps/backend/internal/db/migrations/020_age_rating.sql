-- Child profiles: the oldest age their titles may be rated for (0: no limit) and
-- a fixed bedtime, in minutes after midnight, after which every title stops after its episode.
ALTER TABLE profile ADD COLUMN age_limit INTEGER NOT NULL DEFAULT 0;
ALTER TABLE profile ADD COLUMN bedtime_minutes INTEGER;

-- What countries rate a title, as TMDB spells it. A row with an empty country
-- marks that the ratings were fetched, so titles nobody rated aren't asked again.
CREATE TABLE title_certification (
    media_type    TEXT NOT NULL,
    media_id      INTEGER NOT NULL,
    country       TEXT NOT NULL,
    certification TEXT NOT NULL,
    fetched_at    TEXT NOT NULL,
    PRIMARY KEY (media_type, media_id, country)
);
