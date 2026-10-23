// Filter choices on the search page. Each option maps to the backend filter
// fields it sets; the first option of each group is "Any" and sets nothing.
import type { SearchFilter } from '@neoworks-dev/otter-app/search';

export interface FilterOption {
	value: string;
	label: string;
	filter: SearchFilter;
}

export interface FilterGroup {
	key: string;
	label: string;
	options: FilterOption[];
}

export const RATING_FILTER: FilterGroup = {
	key: 'rating',
	label: 'Rating',
	options: [
		{ value: 'any', label: 'Any', filter: {} },
		{ value: '6', label: '6+', filter: { minRating: 6 } },
		{ value: '7', label: '7+', filter: { minRating: 7 } },
		{ value: '8', label: '8+', filter: { minRating: 8 } }
	]
};

export const LENGTH_FILTER: FilterGroup = {
	key: 'length',
	label: 'Length',
	options: [
		{ value: 'any', label: 'Any', filter: {} },
		{ value: 'short', label: 'Under 90 min', filter: { maxRuntime: 89 } },
		{ value: 'medium', label: '90–120 min', filter: { minRuntime: 90, maxRuntime: 120 } },
		{ value: 'long', label: 'Over 2 h', filter: { minRuntime: 121 } }
	]
};

export const SEASONS_FILTER: FilterGroup = {
	key: 'seasons',
	label: 'Seasons',
	options: [
		{ value: 'any', label: 'Any', filter: {} },
		{ value: 'one', label: '1', filter: { minSeasons: 1, maxSeasons: 1 } },
		{ value: 'few', label: '2–3', filter: { minSeasons: 2, maxSeasons: 3 } },
		{ value: 'many', label: '4+', filter: { minSeasons: 4 } }
	]
};

/** Combines the chosen option of every group into one backend filter. */
export function combineFilters(groups: FilterGroup[], chosen: Record<string, string>): SearchFilter {
	const combined: SearchFilter = {};
	for (const group of groups) {
		const option = group.options.find((entry) => entry.value === chosen[group.key]);
		if (option) Object.assign(combined, option.filter);
	}
	return combined;
}
