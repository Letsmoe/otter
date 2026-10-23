// Resolution or source label of a scene release name, e.g. "1080p" or "DVDRip".
const QUALITY_PATTERN = /\b(2160p|1080p|720p|480p|BluRay|BDRip|WEB-?DL|WEBRip|DVDRip|HDTV)\b/i;

export function qualityOf(releaseName: string): string | undefined {
  const match = QUALITY_PATTERN.exec(releaseName);
  if (!match) return undefined;
  return match[1];
}
