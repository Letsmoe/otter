-- How captions look on the phone and on TVs: outline, box or yellow.
ALTER TABLE profile ADD COLUMN subtitle_style TEXT NOT NULL DEFAULT 'outline';
