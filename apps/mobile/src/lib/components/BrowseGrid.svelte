<script lang="ts">
	import { onMount } from 'svelte';
	import { goto } from '$app/navigation';
	import MediaCard from './MediaCard.svelte';
	import FocusList from './FocusList.svelte';
	import FocusGrid from './FocusGrid.svelte';
	import { sidebarFocus } from '$lib/stores/focus';
	import {
		fetchGenres,
		fetchLibraryPage,
		detailPath,
		LIBRARY_PAGE_SIZE,
		type RailItem,
		type MediaKind
	} from '@neoworks-dev/otter-app/discovery';

	interface Props {
		kind: MediaKind;
		title: string;
	}

	let { kind, title }: Props = $props();

	const GRID_CLASS = 'grid-cols-2 gap-4 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5';

	// The browse API filters by genre only; the bar keeps the pill layout.
	type FilterKey = 'genre';
	const FILTER_KEYS: FilterKey[] = ['genre'];
	const ALL_GENRES_LABEL = 'All genres';

	let genres = $state<string[]>([]);
	let genre = $state('');

	let items = $state<RailItem[]>([]);
	let page = $state(0);
	// Start loading so first paint shows the spinner, not an empty-state flash.
	let loading = $state(true);
	let done = $state(false);
	let sentinel = $state<HTMLElement | null>(null);

	// Which filter overlay is open (null = none).
	let openFilter = $state<FilterKey | null>(null);
	let filterListRef = $state<{ focus: () => void; focusFirst: () => void }>();
	let optionsListRef = $state<{ focus: () => void; focusFirst: () => void }>();
	let gridRef = $state<{ focus: () => void; focusFirst: () => void }>();

	onMount(() => {
		fetchGenres().then((loaded) => (genres = loaded));
	});

	// A genre change resets the grid to its first page.
	$effect(() => {
		loadFirstPage(genre);
	});

	let initialFocusDone = false;
	async function loadFirstPage(selectedGenre: string) {
		loading = true;
		page = 0;
		done = false;
		const first = await fetchLibraryPage(kind, selectedGenre, 0);
		items = first;
		done = first.length < LIBRARY_PAGE_SIZE;
		loading = false;
		// Land focus in the grid the first time results arrive.
		if (!initialFocusDone && first.length > 0) {
			initialFocusDone = true;
			queueMicrotask(() => gridRef?.focusFirst());
		}
	}

	async function loadMore() {
		if (loading || done) return;
		loading = true;
		const nextPage = page + 1;
		const rows = await fetchLibraryPage(kind, genre, nextPage);
		items = [...items, ...rows];
		page = nextPage;
		done = rows.length < LIBRARY_PAGE_SIZE;
		loading = false;
	}

	// Infinite scroll: load the next page when the sentinel scrolls into view.
	$effect(() => {
		if (!sentinel) return;
		const observer = new IntersectionObserver(
			(observed) => {
				if (observed[0]?.isIntersecting) loadMore();
			},
			{ rootMargin: '600px' }
		);
		observer.observe(sentinel);
		return () => observer.disconnect();
	});

	// Refocus the grid when the sidebar closes.
	let sidebarWasOpen = false;
	$effect(() => {
		const open = $sidebarFocus.open;
		if (sidebarWasOpen && !open && !openFilter) {
			queueMicrotask(() => gridRef?.focus());
		}
		sidebarWasOpen = open;
	});

	function pillLabel(): string {
		if (genre) return genre;
		return ALL_GENRES_LABEL;
	}

	type Option = { value: string; label: string };
	function genreOptions(): Option[] {
		const options = genres.map((name) => ({ value: name, label: name }));
		return [{ value: '', label: ALL_GENRES_LABEL }, ...options];
	}

	function openFilterOverlay(key: FilterKey) {
		openFilter = key;
		queueMicrotask(() => optionsListRef?.focusFirst());
	}

	function closeFilter() {
		openFilter = null;
		queueMicrotask(() => filterListRef?.focus());
	}

	function applyOption(value: string) {
		genre = value;
		closeFilter();
	}

	function onBarEdge(direction: 'left' | 'right' | 'up' | 'down') {
		if (direction === 'left') sidebarFocus.openSidebar();
		else if (direction === 'down') gridRef?.focusFirst();
	}

	function onOptionsEdge(direction: 'left' | 'right' | 'up' | 'down') {
		if (direction === 'left' || direction === 'up') closeFilter();
	}

	function onGridEdge(direction: 'left' | 'right' | 'up' | 'down') {
		if (direction === 'left') sidebarFocus.openSidebar();
		else if (direction === 'up') filterListRef?.focus();
		else if (direction === 'down') loadMore();
	}
</script>

<div class="flex flex-col gap-6">
	<div class="flex flex-wrap items-center justify-between gap-4">
		<h1 class="text-3xl font-bold">{title}</h1>

		<FocusList
			bind:this={filterListRef}
			items={FILTER_KEYS}
			orientation="horizontal"
			ariaLabel="Filters"
			onselect={(key) => openFilterOverlay(key)}
			onedge={onBarEdge}
		>
			{#snippet item(_key, _index, focused)}
				<div
					class="flex h-9 items-center rounded-lg px-4 text-sm font-medium {focused
						? 'bg-white text-black'
						: 'bg-base-200 text-base-content/80'}"
				>
					{pillLabel()}
				</div>
			{/snippet}
		</FocusList>
	</div>

	{#if openFilter}
		<!-- svelte-ignore a11y_no_static_element_interactions -->
		<div class="bg-base-200 flex flex-col gap-3 rounded-xl p-4">
			<div class="max-h-72 overflow-y-auto">
				<FocusList
					bind:this={optionsListRef}
					items={genreOptions()}
					orientation="vertical"
					ariaLabel="Options"
					onselect={(option) => applyOption(option.value)}
					onedge={onOptionsEdge}
				>
					{#snippet item(option, _index, focused)}
						<div
							class="flex h-9 items-center gap-2 rounded-lg px-3 text-sm {focused
								? 'bg-white text-black'
								: 'text-base-content/80'}"
						>
							{#if genre === option.value}<span>✓</span>{/if}
							{option.label}
						</div>
					{/snippet}
				</FocusList>
			</div>
		</div>
	{/if}

	{#if items.length === 0 && !loading}
		<div class="flex flex-col items-center gap-3 py-24 text-center">
			<span class="text-6xl opacity-10">🎬</span>
			<p class="text-base-content/40">Nothing here yet. Titles appear once they were searched, trending or recommended.</p>
		</div>
	{:else}
		<FocusGrid
			bind:this={gridRef}
			{items}
			ariaLabel={title}
			gridClass={GRID_CLASS}
			onselect={(item) => goto(detailPath(item))}
			onedge={onGridEdge}
			onnearend={loadMore}
		>
			{#snippet item(entry)}
				<!-- FocusGrid's cell handles click/Enter navigation. -->
				<MediaCard item={entry} onclick={() => {}} />
			{/snippet}
		</FocusGrid>
	{/if}

	{#if loading}
		<div class="flex justify-center py-8">
			<span class="text-base-content/40 text-sm">Loading…</span>
		</div>
	{/if}

	<!-- Infinite-scroll trigger; sits below the grid. -->
	<div bind:this={sentinel} class="h-1"></div>
</div>
