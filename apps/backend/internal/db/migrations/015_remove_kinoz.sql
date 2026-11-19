-- kinoz was removed: since November 2026 kinoz.to redirects every page to an
-- unrelated site, so its stored links lead nowhere.
DELETE FROM stream WHERE plugin = 'kinoz';
