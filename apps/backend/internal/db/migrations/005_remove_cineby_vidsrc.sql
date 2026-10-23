-- cineby and vidsrc were removed: their links were found for every title
-- but never played.
DELETE FROM stream WHERE plugin IN ('cineby', 'vidsrc');
