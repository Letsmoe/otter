import { otter } from './api';
import { TITLE_CARD_SELECT, mediaTypeOf, toRailItem, type MediaKind, type RailItem } from './discovery';

/** Optional narrowing of a search; runtime in minutes, rating on TMDB's 0-10 scale. */
export interface SearchFilter {
	minRating?: number;
	minRuntime?: number;
	maxRuntime?: number;
	minSeasons?: number;
	maxSeasons?: number;
}

export interface SearchOptions {
	kind?: MediaKind;
	filter?: SearchFilter;
	limit?: number;
}

/** TMDB-backed search over movies and series; results are stored titles. */
export async function searchTitles(query: string, options: SearchOptions = {}): Promise<RailItem[]> {
	if (!query.trim()) return [];
	let mediaType;
	if (options.kind) mediaType = mediaTypeOf(options.kind);
	try {
		const data = await otter.query({
			search: {
				__args: { query, mediaType, limit: options.limit || 40, filter: options.filter },
				...TITLE_CARD_SELECT
			}
		});
		return data.search.map(toRailItem);
	} catch {
		return [];
	}
}
