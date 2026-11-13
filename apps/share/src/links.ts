// Mirrors apps/android/.../links/ShareLinks.kt: /movie/603-the-matrix, /tv/1396-breaking-bad[/s1e1].

export type SharedTarget =
	| { kind: 'movie'; tmdbId: number }
	| { kind: 'series'; tmdbId: number }
	| { kind: 'episode'; tmdbId: number; seasonNumber: number; episodeNumber: number };

const episodePattern = /^s(\d+)e(\d+)$/;
// Watch Together room codes: 8 characters without I, O, 0 or 1 (packages/otter-app/src/together.ts).
const roomCodePattern = /^([A-HJ-NP-Z2-9]{4})-?([A-HJ-NP-Z2-9]{4})$/i;

/** The room code of /together/K7QD9MZA (or K7QD-9MZA), upper-cased; null for other paths. */
export function parseInvitePath(path: string): string | null {
	const segments = path.split('/').filter((segment) => segment.length > 0);
	if (segments.length !== 2 || segments[0] !== 'together') {
		return null;
	}
	const match = roomCodePattern.exec(segments[1]);
	if (!match) {
		return null;
	}
	return `${match[1]}${match[2]}`.toUpperCase();
}
const leadingNumber = /^(\d+)/;

export function parseSharePath(path: string): SharedTarget | null {
	const segments = path.split('/').filter((segment) => segment.length > 0);
	if (segments.length < 2) {
		return null;
	}
	const idMatch = leadingNumber.exec(segments[1]);
	if (!idMatch) {
		return null;
	}
	const tmdbId = Number(idMatch[1]);
	if (segments[0] === 'movie') {
		return { kind: 'movie', tmdbId };
	}
	if (segments[0] !== 'tv') {
		return null;
	}
	const episodeMatch = episodePattern.exec((segments[2] ?? '').toLowerCase());
	if (!episodeMatch) {
		return { kind: 'series', tmdbId };
	}
	return { kind: 'episode', tmdbId, seasonNumber: Number(episodeMatch[1]), episodeNumber: Number(episodeMatch[2]) };
}

/** First language of an Accept-Language header as TMDB wants it ("de-DE"); English otherwise. */
export function tmdbLanguage(acceptLanguage: string | null): string {
	const first = (acceptLanguage ?? '').split(',')[0].split(';')[0].trim();
	if (/^[a-z]{2}-[A-Z]{2}$/.test(first)) {
		return first;
	}
	if (/^[a-z]{2}$/.test(first)) {
		return first;
	}
	return 'en-US';
}
