<script lang="ts">
	// Netflix-style search: back button and search bar, a type switch (All,
	// Movies, TV Shows) and, for one type, filters for rating plus length or seasons.
	import { onMount } from 'svelte';
	import ArrowLeftIcon from 'phosphor-svelte/lib/ArrowLeftIcon';
	import MagnifyingGlassIcon from 'phosphor-svelte/lib/MagnifyingGlassIcon';
	import XCircleIcon from 'phosphor-svelte/lib/XCircleIcon';
	import { searchTitles } from '@neoworks-dev/otter-app/search';
	import {
		detailPath,
		fetchRelated,
		fetchTrending,
		onlyKind,
		withoutRepeats,
		type MediaKind,
		type RailItem
	} from '@neoworks-dev/otter-app/discovery';
	import PosterRail from '$lib/components/home/PosterRail.svelte';
	import SegmentedBar from '$lib/components/SegmentedBar.svelte';
	import FilterSelect from '$lib/components/FilterSelect.svelte';
	import {
		LENGTH_FILTER,
		RATING_FILTER,
		SEASONS_FILTER,
		combineFilters,
		type FilterGroup
	} from '$lib/searchFilters';

	type SearchType = 'all' | MediaKind;

	const TYPE_OPTIONS: { value: SearchType; label: string }[] = [
		{ value: 'all', label: 'All' },
		{ value: 'movie', label: 'Movies' },
		{ value: 'series', label: 'TV Shows' }
	];

	const DEBOUNCE_MS = 300;
	const TOP_RESULT_COUNT = 6;

	let query = $state('');
	let searchType = $state<SearchType>('all');
	let chosenFilters = $state<Record<string, string>>({});
	let results = $state<RailItem[]>([]);
	let searching = $state(false);
	let trending = $state<RailItem[]>([]);
	// "More Like X" rows for the best movie and the best show of the results.
	let relatedRows = $state<{ title: string; items: RailItem[] }[]>([]);
	let input = $state<HTMLInputElement>();

	let debounceTimer: ReturnType<typeof setTimeout> | null = null;
	let requestToken = 0;

	let hasQuery = $derived(query.trim().length > 0);
	let filterGroups = $derived(groupsFor(searchType));

	let resultRails = $derived(
		withoutRepeats([
			results.slice(0, TOP_RESULT_COUNT),
			onlyKind(results, 'movie'),
			onlyKind(results, 'series'),
			...relatedRows.map((row) => row.items)
		])
	);

	function groupsFor(type: SearchType): FilterGroup[] {
		if (type === 'movie') return [RATING_FILTER, LENGTH_FILTER];
		if (type === 'series') return [RATING_FILTER, SEASONS_FILTER];
		return [];
	}

	function chosenValue(group: FilterGroup): string {
		return chosenFilters[group.key] || group.options[0].value;
	}

	onMount(() => {
		input?.focus();
		fetchTrending().then((items) => (trending = items));
	});

	async function runSearch() {
		requestToken += 1;
		const token = requestToken;
		if (!query.trim()) {
			results = [];
			relatedRows = [];
			searching = false;
			return;
		}
		searching = true;
		let kind: MediaKind | undefined;
		if (searchType !== 'all') kind = searchType;
		const found = await searchTitles(query, {
			kind,
			filter: combineFilters(filterGroups, chosenFilters)
		});
		if (token !== requestToken) return;
		results = found;
		searching = false;
		loadRelatedRows(found, token);
	}

	async function loadRelatedRows(found: RailItem[], token: number) {
		relatedRows = [];
		const seeds: RailItem[] = [];
		const bestMovie = found.find((item) => item.mediaType === 'movie');
		const bestShow = found.find((item) => item.mediaType === 'series');
		if (bestMovie) seeds.push(bestMovie);
		if (bestShow) seeds.push(bestShow);
		const rows = await Promise.all(
			seeds.map(async (seed) => ({ title: `More Like ${seed.title}`, items: await fetchRelated(seed.id) }))
		);
		if (token !== requestToken) return;
		relatedRows = rows;
	}

	function onInput() {
		if (debounceTimer) clearTimeout(debounceTimer);
		debounceTimer = setTimeout(runSearch, DEBOUNCE_MS);
	}

	function selectType(type: SearchType) {
		searchType = type;
		chosenFilters = {};
		runSearch();
	}

	function selectFilter(group: FilterGroup, value: string) {
		chosenFilters = { ...chosenFilters, [group.key]: value };
		runSearch();
	}

	function clear() {
		query = '';
		runSearch();
		input?.focus();
	}

	function goBack() {
		history.back();
	}
</script>

<div class="flex flex-col gap-4">
	<header class="flex items-center gap-1 pt-2">
		<button
			class="-ml-2 flex h-11 w-11 shrink-0 items-center justify-center"
			aria-label="Back"
			onclick={goBack}
		>
			<ArrowLeftIcon size={24} />
		</button>
		<label class="glass-bar flex h-11 flex-1 items-center gap-2 rounded-full px-4">
			<MagnifyingGlassIcon size={22} class="shrink-0 opacity-70" />
			<input
				bind:this={input}
				bind:value={query}
				oninput={onInput}
				type="search"
				enterkeyhint="search"
				placeholder="Search movies and shows"
				class="min-w-0 flex-1 bg-transparent text-base outline-none placeholder:text-white/40 [&::-webkit-search-cancel-button]:appearance-none"
			/>
			{#if hasQuery}
				<button class="flex h-8 w-8 items-center justify-center" aria-label="Clear" onclick={clear}>
					<XCircleIcon size={22} weight="fill" class="opacity-70" />
				</button>
			{/if}
		</label>
	</header>

	<SegmentedBar options={TYPE_OPTIONS} selected={searchType} onselect={selectType} />

	{#if filterGroups.length > 0}
		<div class="no-scrollbar -mx-3 flex gap-2 overflow-x-auto px-3">
			{#each filterGroups as group (group.key)}
				<FilterSelect
					label={group.label}
					options={group.options}
					selected={chosenValue(group)}
					onselect={(value) => selectFilter(group, value)}
				/>
			{/each}
		</div>
	{/if}

	{#if !hasQuery}
		<PosterRail title="Trending Now" items={trending} />
	{:else if searching && results.length === 0}
		<span class="loading loading-spinner mx-auto mt-8"></span>
	{:else if results.length === 0}
		<p class="mt-8 text-center text-sm opacity-60">No results for “{query.trim()}”.</p>
	{:else if searchType === 'all'}
		<div class="flex flex-col gap-6 pt-2">
			<PosterRail title="Top Results" items={resultRails[0]} size="large" />
			<PosterRail title="More Movies" items={resultRails[1]} />
			<PosterRail title="More TV Shows" items={resultRails[2]} />
			{#each relatedRows as row, index (row.title)}
				<PosterRail title={row.title} items={resultRails[3 + index]} />
			{/each}
		</div>
	{:else}
		<div class="grid grid-cols-3 gap-2 pt-2" class:opacity-60={searching}>
			{#each results as item (item.id)}
				<a href={detailPath(item)} class="aspect-[2/3] overflow-hidden rounded-md bg-white/5">
					{#if item.posterUrl}
						<img src={item.posterUrl} alt={item.title} loading="lazy" class="h-full w-full object-cover" />
					{:else}
						<span class="flex h-full items-center justify-center p-2 text-center text-xs">
							{item.title}
						</span>
					{/if}
				</a>
			{/each}
		</div>
	{/if}
</div>
