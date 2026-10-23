// TMDB serves images from a CDN keyed by a relative path (e.g. "/abc.jpg").
const TMDB_IMAGE_BASE = 'https://image.tmdb.org/t/p';

export type PosterSize = 'w185' | 'w342' | 'w500' | 'original';
export type BackdropSize = 'w780' | 'w1280' | 'original';

// Provider/enriched posters are stored as absolute URLs (e.g. a filmpalast or
// MinIO link). TMDB posters are stored as CDN-relative paths ("/abc.jpg"). Pass
// absolute URLs through untouched; only TMDB-relative paths get the CDN prefix.
function isAbsolute(path: string): boolean {
	return path.startsWith('http://') || path.startsWith('https://') || path.startsWith('data:');
}

export function posterUrl(path: string | null | undefined, size: PosterSize = 'w500'): string | null {
	if (!path) return null;
	if (isAbsolute(path)) return path;
	return `${TMDB_IMAGE_BASE}/${size}${path}`;
}

export function backdropUrl(
	path: string | null | undefined,
	size: BackdropSize = 'w1280'
): string | null {
	if (!path) return null;
	if (isAbsolute(path)) return path;
	return `${TMDB_IMAGE_BASE}/${size}${path}`;
}

// Release dates are stored as YYYY-MM-DD; pull the year for compact display.
export function yearOf(releaseDate: string | null | undefined): number | null {
	if (!releaseDate) return null;
	const year = parseInt(releaseDate.slice(0, 4), 10);
	return Number.isNaN(year) ? null : year;
}

export function formatRuntime(minutes: number | null | undefined): string | null {
	if (!minutes) return null;
	const hours = Math.floor(minutes / 60);
	const rem = minutes % 60;
	return hours ? `${hours}h ${rem}m` : `${rem}m`;
}

export function formatSpeed(bytesPerSecond: number | null | undefined): string | null {
	if (!bytesPerSecond) return null;
	if (bytesPerSecond >= 1_000_000) return `${(bytesPerSecond / 1_000_000).toFixed(1)} MB/s`;
	if (bytesPerSecond >= 1_000) return `${(bytesPerSecond / 1_000).toFixed(0)} KB/s`;
	return `${Math.round(bytesPerSecond)} B/s`;
}
