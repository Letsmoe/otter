import type { SharedTarget } from './links';

const apiBase = 'https://api.themoviedb.org/3';
const imageBase = 'https://image.tmdb.org/t/p';
// Titles barely change; Cloudflare's cache keeps TMDB answers this long.
const cacheSeconds = 24 * 60 * 60;

/** What a share page shows about a movie, series or episode. */
export interface SharedTitle {
	heading: string;
	subheading: string | null;
	overview: string;
	posterUrl: string | null;
	/** Wide image for link previews: the episode still or the backdrop. */
	previewImageUrl: string | null;
	previewType: 'video.movie' | 'video.tv_show' | 'video.episode';
}

interface TmdbTitle {
	title?: string;
	name?: string;
	overview?: string;
	release_date?: string;
	first_air_date?: string;
	poster_path?: string | null;
	backdrop_path?: string | null;
}

interface TmdbEpisode {
	name?: string;
	overview?: string;
	still_path?: string | null;
}

/** Looks the target up on TMDB; null when TMDB doesn't know it. */
export async function fetchSharedTitle(target: SharedTarget, apiKey: string, language: string): Promise<SharedTitle | null> {
	if (target.kind === 'movie') {
		const movie = await fetchJson<TmdbTitle>(`/movie/${target.tmdbId}`, apiKey, language);
		if (!movie) {
			return null;
		}
		return {
			heading: titleWithYear(movie.title, movie.release_date),
			subheading: null,
			overview: movie.overview ?? '',
			posterUrl: imageUrl(movie.poster_path, 'w500'),
			previewImageUrl: imageUrl(movie.backdrop_path ?? movie.poster_path, 'w1280'),
			previewType: 'video.movie',
		};
	}
	const series = await fetchJson<TmdbTitle>(`/tv/${target.tmdbId}`, apiKey, language);
	if (!series) {
		return null;
	}
	const seriesTitle = titleWithYear(series.name, series.first_air_date);
	if (target.kind === 'series') {
		return {
			heading: seriesTitle,
			subheading: null,
			overview: series.overview ?? '',
			posterUrl: imageUrl(series.poster_path, 'w500'),
			previewImageUrl: imageUrl(series.backdrop_path ?? series.poster_path, 'w1280'),
			previewType: 'video.tv_show',
		};
	}
	const episodePath = `/tv/${target.tmdbId}/season/${target.seasonNumber}/episode/${target.episodeNumber}`;
	const episode = await fetchJson<TmdbEpisode>(episodePath, apiKey, language);
	if (!episode) {
		return null;
	}
	const code = `S${target.seasonNumber}:E${target.episodeNumber}`;
	return {
		heading: `${series.name ?? ''} ${code}`.trim(),
		subheading: episode.name ?? null,
		overview: episode.overview || series.overview || '',
		posterUrl: imageUrl(series.poster_path, 'w500'),
		previewImageUrl: imageUrl(episode.still_path ?? series.backdrop_path, 'w1280'),
		previewType: 'video.episode',
	};
}

async function fetchJson<Body>(path: string, apiKey: string, language: string): Promise<Body | null> {
	const url = `${apiBase}${path}?api_key=${encodeURIComponent(apiKey)}&language=${encodeURIComponent(language)}`;
	const response = await fetch(url, { cf: { cacheTtl: cacheSeconds, cacheEverything: true } } as RequestInit);
	if (response.status === 404) {
		return null;
	}
	if (!response.ok) {
		throw new Error(`TMDB ${path}: HTTP ${response.status}`);
	}
	return (await response.json()) as Body;
}

function titleWithYear(title: string | undefined, date: string | undefined): string {
	const year = (date ?? '').slice(0, 4);
	if (!year) {
		return title ?? '';
	}
	return `${title ?? ''} (${year})`;
}

function imageUrl(path: string | null | undefined, size: string): string | null {
	if (!path) {
		return null;
	}
	return `${imageBase}/${size}${path}`;
}
