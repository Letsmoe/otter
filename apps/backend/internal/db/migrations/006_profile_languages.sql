-- Audio languages a profile wants, most preferred first, as a JSON array of
-- ISO 639-1 codes. Links in these languages rank first; dubs before subs.
ALTER TABLE profile ADD COLUMN languages TEXT NOT NULL DEFAULT '["de","en"]';
