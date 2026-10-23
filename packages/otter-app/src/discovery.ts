// Data helpers for the home rails, browse grids, watchlist, ratings, history
// and playback progress.
import { otter } from './api';
import { posterUrl, backdropUrl } from './tmdb';
import type { MediaType } from '@neoworks-dev/otter-sdk/client';

export type MediaKind = 'movie' | 'series';

/** What a poster card shows. */
export interface CardItem {
	id: string;
	title: string;
	posterUrl: string | null;
	subtitle?: string | null;
}

export interface RailItem extends CardItem {
	mediaType: MediaKind;
	backdropUrl: string | null;
	overview?: string | null;
	// Watch fraction (0..1) for continue-watching and history; undefined elsewhere.
	progress?: number;
	// Overrides the default detail link, e.g. to open a specific episode.
	href?: string;
	// Short label over the poster, e.g. a release date for upcoming titles.
	badge?: string;
	year?: number | null;
	// When the profile last watched it; history only.
	watchedAt?: string;
}

/** Title fields every card needs; spread into larger selections. */
export const TITLE_CARD_SELECT = {
	id: true,
	mediaType: true,
	title: true,
	year: true,
	overview: true,
	posterPath: true,
	backdropPath: true
} as const;

const EPISODE_REF_SELECT = {
	id: true,
	seasonNumber: true,
	episodeNumber: true,
	name: true
} as const;

const PROGRESS_SELECT = { progressSeconds: true, durationSeconds: true, updatedAt: true } as const;

export interface TitleCard {
	id: string;
	mediaType: MediaType;
	title: string;
	year: number | null;
	overview: string | null;
	posterPath: string | null;
	backdropPath: string | null;
}

export interface EpisodeRef {
	id: string;
	seasonNumber: number;
	episodeNumber: number;
	name: string | null;
}

interface ProgressRef {
	progressSeconds: number;
	durationSeconds: number;
	updatedAt?: string;
}

interface WatchedEntry {
	title: TitleCard;
	episode: EpisodeRef | null;
	progress: ProgressRef | null;
}

export function mediaKindOf(mediaType: MediaType): MediaKind {
	if (mediaType === 'SERIES') return 'series';
	return 'movie';
}

export function mediaTypeOf(kind: MediaKind): MediaType {
	if (kind === 'series') return 'SERIES';
	return 'MOVIE';
}

function yearLabel(year: number | null): string | null {
	if (!year) return null;
	return String(year);
}

export function toRailItem(title: TitleCard): RailItem {
	return {
		id: title.id,
		mediaType: mediaKindOf(title.mediaType),
		title: title.title,
		posterUrl: posterUrl(title.posterPath),
		backdropUrl: backdropUrl(title.backdropPath),
		overview: title.overview,
		subtitle: yearLabel(title.year),
		year: title.year
	};
}

export function detailPath(item: { id: string; mediaType: string; href?: string }): string {
	if (item.href) return item.href;
	if (item.mediaType === 'series') return `/series/${item.id}`;
	return `/movies/${item.id}`;
}

/** Series page link that opens one episode (and its season). */
export function episodePath(seriesId: string, episode: EpisodeRef): string {
	const query = `season=${episode.seasonNumber}&episode=${encodeURIComponent(episode.id)}`;
	return `/series/${seriesId}?${query}`;
}

export function episodeLabel(episode: EpisodeRef): string {
	const code = `S${episode.seasonNumber}E${episode.episodeNumber}`;
	if (!episode.name) return code;
	return `${code} · ${episode.name}`;
}

function progressFraction(progress: ProgressRef | null): number | undefined {
	if (!progress || progress.durationSeconds <= 0) return undefined;
	const fraction = progress.progressSeconds / progress.durationSeconds;
	return Math.min(1, Math.max(0, fraction));
}

function toWatchedItem(entry: WatchedEntry): RailItem {
	const item = toRailItem(entry.title);
	item.progress = progressFraction(entry.progress);
	if (entry.progress?.updatedAt) item.watchedAt = entry.progress.updatedAt;
	if (entry.episode) {
		item.subtitle = episodeLabel(entry.episode);
		item.href = episodePath(entry.title.id, entry.episode);
	}
	return item;
}

export async function fetchContinueWatching(profileId: string, limit = 20): Promise<RailItem[]> {
	try {
		const data = await otter.query({
			continueWatching: {
				__args: { profileId, limit },
				title: TITLE_CARD_SELECT,
				episode: EPISODE_REF_SELECT,
				progress: PROGRESS_SELECT
			}
		});
		return data.continueWatching.map(toWatchedItem);
	} catch {
		return [];
	}
}

export async function fetchHistory(profileId: string, limit = 50): Promise<RailItem[]> {
	try {
		const data = await otter.query({
			history: {
				__args: { profileId, limit },
				title: TITLE_CARD_SELECT,
				episode: EPISODE_REF_SELECT,
				progress: PROGRESS_SELECT
			}
		});
		return data.history.map(toWatchedItem);
	} catch {
		return [];
	}
}

export interface RecommendationRail {
	key: string;
	// Row reason, e.g. "Because you watched Heat".
	title: string;
	items: RailItem[];
}

export async function fetchRecommendationRows(
	profileId: string,
	rows = 8,
	perRow = 20
): Promise<RecommendationRail[]> {
	try {
		const data = await otter.query({
			recommendationRows: {
				__args: { profileId, rows, perRow },
				key: true,
				title: true,
				items: TITLE_CARD_SELECT
			}
		});
		return data.recommendationRows.map((row) => ({
			key: row.key,
			title: row.title,
			items: row.items.map(toRailItem)
		}));
	} catch {
		return [];
	}
}

/** Trending titles from TMDB; both kinds when no kind is given. */
export async function fetchTrending(kind?: MediaKind): Promise<RailItem[]> {
	let mediaType: MediaType | undefined;
	if (kind) mediaType = mediaTypeOf(kind);
	try {
		const data = await otter.query({
			trending: { __args: { mediaType }, ...TITLE_CARD_SELECT }
		});
		return data.trending.map(toRailItem);
	} catch {
		return [];
	}
}

/** Genre names for the browse grid filter, alphabetical. */
export async function fetchGenres(): Promise<string[]> {
	try {
		const data = await otter.query({ genres: true });
		return [...data.genres].sort((first, second) => first.localeCompare(second));
	} catch {
		return [];
	}
}

export const LIBRARY_PAGE_SIZE = 40;

/** One page of stored titles of one kind, most popular first. */
export async function fetchLibraryPage(
	kind: MediaKind,
	genre: string,
	page: number,
	limit = LIBRARY_PAGE_SIZE
): Promise<RailItem[]> {
	let genreFilter: string | undefined;
	if (genre) genreFilter = genre;
	try {
		const data = await otter.query({
			browse: {
				__args: {
					mediaType: mediaTypeOf(kind),
					genre: genreFilter,
					limit,
					offset: page * limit
				},
				...TITLE_CARD_SELECT
			}
		});
		return data.browse.map(toRailItem);
	} catch {
		return [];
	}
}

export async function fetchWatchlist(profileId: string): Promise<RailItem[]> {
	try {
		const data = await otter.query({
			watchlist: { __args: { profileId }, ...TITLE_CARD_SELECT }
		});
		return data.watchlist.map(toRailItem);
	} catch {
		return [];
	}
}

/** Titles the profile rated "I like this" or better, latest first. */
export async function fetchLikedTitles(profileId: string, limit = 30): Promise<RailItem[]> {
	try {
		const data = await otter.query({
			likedTitles: { __args: { profileId, limit }, ...TITLE_CARD_SELECT }
		});
		return data.likedTitles.map(toRailItem);
	} catch {
		return [];
	}
}

export async function fetchInWatchlist(profileId: string, mediaId: string): Promise<boolean> {
	try {
		const data = await otter.query({
			title: { __args: { id: mediaId }, inWatchlist: { __args: { profileId } } }
		});
		if (!data.title) return false;
		return data.title.inWatchlist;
	} catch {
		return false;
	}
}

export async function addToWatchlist(profileId: string, mediaId: string): Promise<void> {
	await otter.mutation({ addToWatchlist: { __args: { profileId, mediaId } } });
}

export async function removeFromWatchlist(profileId: string, mediaId: string): Promise<void> {
	await otter.mutation({ removeFromWatchlist: { __args: { profileId, mediaId } } });
}

/** The profile's rating on the 1–10 scale, null when unrated. */
export async function fetchUserRating(profileId: string, mediaId: string): Promise<number | null> {
	try {
		const data = await otter.query({
			title: { __args: { id: mediaId }, rating: { __args: { profileId } } }
		});
		if (!data.title) return null;
		return data.title.rating;
	} catch {
		return null;
	}
}

export async function setUserRating(
	profileId: string,
	mediaId: string,
	rating: number
): Promise<void> {
	await otter.mutation({ setRating: { __args: { profileId, mediaId, rating } } });
}

export async function clearUserRating(profileId: string, mediaId: string): Promise<void> {
	await otter.mutation({ clearRating: { __args: { profileId, mediaId } } });
}

/** Records playback progress for resume + recommendations. Best-effort. */
export async function recordProgress(input: {
	profileId: string;
	mediaId: string;
	progressSeconds: number;
	durationSeconds: number;
}): Promise<void> {
	try {
		await otter.mutation({
			recordProgress: {
				__args: {
					profileId: input.profileId,
					mediaId: input.mediaId,
					progressSeconds: Math.floor(input.progressSeconds),
					durationSeconds: Math.floor(input.durationSeconds)
				},
				completed: true
			}
		});
	} catch {
		// Progress tracking must never interrupt playback.
	}
}

export interface SeasonSummary {
	id: string;
	seasonNumber: number;
	name: string | null;
	episodeCount: number;
	posterUrl: string | null;
}

/** Seasons of a series, ordered by season number, for the hero season selector. */
export async function fetchSeasons(seriesId: string): Promise<SeasonSummary[]> {
	try {
		const data = await otter.query({
			title: {
				__args: { id: seriesId },
				seasons: { id: true, seasonNumber: true, name: true, episodeCount: true, posterPath: true }
			}
		});
		if (!data.title) return [];
		const seasons = data.title.seasons.map((season) => ({
			id: season.id,
			seasonNumber: season.seasonNumber,
			name: season.name,
			episodeCount: season.episodeCount,
			posterUrl: posterUrl(season.posterPath)
		}));
		return seasons.sort((first, second) => first.seasonNumber - second.seasonNumber);
	} catch {
		return [];
	}
}

/** YouTube key of a title's trailer; reading it fetches TMDB detail on first use. */
export async function fetchTrailerKey(mediaId: string): Promise<string | null> {
	try {
		const data = await otter.query({ title: { __args: { id: mediaId }, trailerKey: true } });
		if (!data.title) return null;
		return data.title.trailerKey;
	} catch {
		return null;
	}
}

export interface FeaturedItem extends RailItem {
	genres: string[];
}

const FEATURED_SELECT = { ...TITLE_CARD_SELECT, genres: true } as const;

function toFeaturedItem(title: TitleCard & { genres: string[] }): FeaturedItem {
	return { ...toRailItem(title), genres: title.genres };
}

function firstWithPoster(titles: (TitleCard & { genres: string[] })[]): FeaturedItem | null {
	const withPoster = titles.find((title) => title.posterPath);
	if (!withPoster) return null;
	return toFeaturedItem(withPoster);
}

/**
 * The title to feature at the top of a home page: the profile's best
 * recommendation of that kind, else the top trending one.
 */
export async function fetchFeatured(
	profileId: string,
	kind: MediaKind
): Promise<FeaturedItem | null> {
	const mediaType = mediaTypeOf(kind);
	try {
		const data = await otter.query({
			recommendations: { __args: { profileId, mediaType, limit: 10 }, title: FEATURED_SELECT }
		});
		const recommended = firstWithPoster(data.recommendations.map((entry) => entry.title));
		if (recommended) return recommended;
		const trending = await otter.query({ trending: { __args: { mediaType }, ...FEATURED_SELECT } });
		return firstWithPoster(trending.trending);
	} catch {
		return null;
	}
}

/** The profile's recommendations of one kind, best first. */
export async function fetchRecommended(
	profileId: string,
	kind: MediaKind,
	limit = 24
): Promise<RailItem[]> {
	try {
		const data = await otter.query({
			recommendations: {
				__args: { profileId, mediaType: mediaTypeOf(kind), limit },
				title: TITLE_CARD_SELECT
			}
		});
		return data.recommendations.map((entry) => toRailItem(entry.title));
	} catch {
		return [];
	}
}

/** Keeps the items of one kind, for pages that show only movies or only series. */
export function onlyKind(items: RailItem[], kind: MediaKind): RailItem[] {
	return items.filter((item) => item.mediaType === kind);
}

/** Finished titles of one kind, most recently watched first, without repeats. */
export async function fetchWatchAgain(profileId: string, kind: MediaKind): Promise<RailItem[]> {
	try {
		const data = await otter.query({
			history: {
				__args: { profileId, limit: 100 },
				title: TITLE_CARD_SELECT,
				progress: { completed: true }
			}
		});
		const seen = new Set<string>();
		const items: RailItem[] = [];
		for (const entry of data.history) {
			if (!entry.progress.completed || seen.has(entry.title.id)) continue;
			seen.add(entry.title.id);
			items.push(toRailItem(entry.title));
		}
		return onlyKind(items, kind);
	} catch {
		return [];
	}
}

function releaseBadge(releaseDate: string | null, today: string): string | undefined {
	if (!releaseDate || releaseDate <= today) return undefined;
	const date = new Date(`${releaseDate}T00:00:00`);
	return date.toLocaleDateString(undefined, { month: 'short', day: 'numeric' });
}

/** Upcoming releases (with a date badge), then recent ones. */
export async function fetchNewAndComingSoon(kind: MediaKind): Promise<RailItem[]> {
	try {
		const data = await otter.query({
			newAndComingSoon: {
				__args: { mediaType: mediaTypeOf(kind) },
				...TITLE_CARD_SELECT,
				releaseDate: true
			}
		});
		const today = new Date().toISOString().slice(0, 10);
		return data.newAndComingSoon.map((title) => {
			const item = toRailItem(title);
			item.badge = releaseBadge(title.releaseDate, today);
			return item;
		});
	} catch {
		return [];
	}
}

export interface MoreWithRail {
	personName: string;
	becauseOf: string;
	items: RailItem[];
}

/** Titles with the lead actor of the profile's latest well-rated title of this kind. */
export async function fetchMoreWith(
	profileId: string,
	kind: MediaKind
): Promise<MoreWithRail | null> {
	try {
		const data = await otter.query({
			moreWith: {
				__args: { profileId, mediaType: mediaTypeOf(kind) },
				person: { name: true },
				becauseOf: { title: true },
				items: TITLE_CARD_SELECT
			}
		});
		if (!data.moreWith) return null;
		return {
			personName: data.moreWith.person.name,
			becauseOf: data.moreWith.becauseOf.title,
			items: data.moreWith.items.map(toRailItem)
		};
	} catch {
		return null;
	}
}

export interface GenreRail {
	genre: string;
	items: RailItem[];
}

function topGenres(titles: { genres: string[] }[], count: number): string[] {
	const counts = new Map<string, number>();
	for (const title of titles) {
		for (const genre of title.genres) {
			counts.set(genre, (counts.get(genre) || 0) + 1);
		}
	}
	return [...counts.entries()]
		.sort((left, right) => right[1] - left[1])
		.slice(0, count)
		.map(([genre]) => genre);
}

/** One rail per genre the profile's recommendations lean towards most. */
export async function fetchGenreRails(
	profileId: string,
	kind: MediaKind,
	count = 3
): Promise<GenreRail[]> {
	const mediaType = mediaTypeOf(kind);
	try {
		const data = await otter.query({
			recommendations: { __args: { profileId, mediaType, limit: 40 }, title: { genres: true } }
		});
		const genres = topGenres(
			data.recommendations.map((entry) => entry.title),
			count
		);
		return await Promise.all(
			genres.map(async (genre) => {
				const browse = await otter.query({
					browse: { __args: { mediaType, genre, limit: 20 }, ...TITLE_CARD_SELECT }
				});
				return { genre, items: browse.browse.map(toRailItem) };
			})
		);
	} catch {
		return [];
	}
}

/**
 * Drops titles already shown by an earlier rail (or earlier in the same rail),
 * so every title appears once per page. TMDB sometimes lists one film under
 * several ids, so a matching name and year counts as the same title too.
 * `alreadyShown` holds ids shown above the rails (e.g. a featured title).
 */
export function withoutRepeats(rails: RailItem[][], alreadyShown: string[] = []): RailItem[][] {
	const seen = new Set(alreadyShown);
	return rails.map((items) =>
		items.filter((item) => {
			const nameKey = `${item.mediaType}|${item.title.toLowerCase()}|${item.subtitle || ''}`;
			if (seen.has(item.id) || seen.has(nameKey)) return false;
			seen.add(item.id);
			seen.add(nameKey);
			return true;
		})
	);
}

/** Titles related to one title (TMDB recommendations, then shared people). */
export async function fetchRelated(mediaId: string, limit = 20): Promise<RailItem[]> {
	try {
		const data = await otter.query({
			title: { __args: { id: mediaId }, related: { __args: { limit }, ...TITLE_CARD_SELECT } }
		});
		if (!data.title) return [];
		return data.title.related.map(toRailItem);
	} catch {
		return [];
	}
}

// Positions this close to the start or end aren't worth resuming.
const RESUME_MIN_SECONDS = 30;

/** Where to resume a movie or episode for the profile, or 0 to start over. */
export async function fetchResumePosition(profileId: string, mediaId: string): Promise<number> {
	const progressSelect = {
		progress: { __args: { profileId }, progressSeconds: true, completed: true }
	};
	try {
		let progress: { progressSeconds: number; completed: boolean } | null | undefined;
		if (mediaId.startsWith('episode:')) {
			const data = await otter.query({ episode: { __args: { id: mediaId }, ...progressSelect } });
			progress = data.episode?.progress;
		} else {
			const data = await otter.query({ title: { __args: { id: mediaId }, ...progressSelect } });
			progress = data.title?.progress;
		}
		if (!progress || progress.completed || progress.progressSeconds < RESUME_MIN_SECONDS) return 0;
		return progress.progressSeconds;
	} catch {
		return 0;
	}
}

export type ListOrder = 'added' | 'title' | 'release';

function releaseYear(item: RailItem): number {
	if (!item.year) return 0;
	return item.year;
}

/**
 * Orders a list: 'added' keeps the given (newest saved first) order, 'title'
 * sorts A–Z, 'release' newest release first.
 */
export function sortRailItems(items: RailItem[], order: ListOrder): RailItem[] {
	const sorted = [...items];
	if (order === 'title') sorted.sort((first, second) => first.title.localeCompare(second.title));
	if (order === 'release') sorted.sort((first, second) => releaseYear(second) - releaseYear(first));
	return sorted;
}

/** A trending title shown behind the profile picker. */
export interface Spotlight {
	id: string;
	title: string;
	posterUrl: string;
	logoUrl: string | null;
	tagline: string | null;
}

const SPOTLIGHT_CANDIDATES = 8;

/** One of the top trending titles with poster, logo and tagline; null offline. */
export async function fetchSpotlight(): Promise<Spotlight | null> {
	const trending = (await fetchTrending()).filter((item) => item.posterUrl);
	const candidates = trending.slice(0, SPOTLIGHT_CANDIDATES);
	if (candidates.length === 0) return null;
	const picked = candidates[Math.floor(Math.random() * candidates.length)];
	try {
		const data = await otter.query({
			title: { __args: { id: picked.id }, logoPath: true, tagline: true, posterPath: true }
		});
		return {
			id: picked.id,
			title: picked.title,
			posterUrl: posterUrl(data.title?.posterPath, 'original') || picked.posterUrl || '',
			logoUrl: posterUrl(data.title?.logoPath, 'w500'),
			tagline: data.title?.tagline || null
		};
	} catch {
		return {
			id: picked.id,
			title: picked.title,
			posterUrl: picked.posterUrl || '',
			logoUrl: null,
			tagline: null
		};
	}
}
