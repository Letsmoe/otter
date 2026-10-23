<script lang="ts">
	import { goto } from '$app/navigation';
	import MagnifyingGlassIcon from 'phosphor-svelte/lib/MagnifyingGlassIcon';
	import SpinnerGapIcon from 'phosphor-svelte/lib/SpinnerGapIcon';
	import XIcon from 'phosphor-svelte/lib/XIcon';
	import { searchTitles } from '@neoworks-dev/otter-app/search';
	import { detailPath, type RailItem } from '@neoworks-dev/otter-app/discovery';

	let query = $state('');
	let results = $state<RailItem[]>([]);
	let loading = $state(false);

	let debounceTimer: ReturnType<typeof setTimeout> | null = null;
	let requestToken = 0;

	let inputEl = $state<HTMLInputElement>();

	let resultCount = $derived(results.length);

	async function runSearch(text: string) {
		requestToken += 1;
		const token = requestToken;
		if (!text.trim()) {
			results = [];
			loading = false;
			return;
		}
		loading = true;
		const found = await searchTitles(text);
		if (token !== requestToken) return;
		results = found;
		loading = false;
	}

	function scheduleSearch() {
		if (debounceTimer) clearTimeout(debounceTimer);
		debounceTimer = setTimeout(() => runSearch(query), 300);
	}

	function clearQuery() {
		query = '';
		scheduleSearch();
		inputEl?.focus();
	}

	function kindLabel(result: RailItem): string {
		if (result.mediaType === 'series') return 'Series';
		return 'Movie';
	}

	// Focus the field on entry so typing starts immediately.
	$effect(() => {
		queueMicrotask(() => inputEl?.focus());
	});
</script>

<div class="flex w-full flex-col gap-6">
	<h1 class="text-2xl font-bold tracking-tight">Search</h1>

	<!-- Source badge: SFW search is TMDB-backed. -->
	<div class="flex flex-wrap items-center gap-2">
		<span class="text-base-content/40 text-xs font-medium uppercase">Sources</span>
		<span class="border-base-300 text-base-content/60 rounded-full border px-3 py-1 text-sm font-medium">
			TMDB
		</span>
	</div>

	<!-- Search field -->
	<div
		class="border-base-300 bg-base-100 focus-within:border-base-content/30 flex items-center gap-3 rounded-md border px-3 py-2 transition-colors"
	>
		{#if loading}
			<SpinnerGapIcon size={20} class="text-primary shrink-0 animate-spin" />
		{:else}
			<MagnifyingGlassIcon size={20} class="text-base-content/40 shrink-0" />
		{/if}
		<input
			bind:this={inputEl}
			bind:value={query}
			oninput={scheduleSearch}
			type="text"
			placeholder="Search movies & series…"
			class="placeholder:text-base-content/30 min-w-0 flex-1 bg-transparent text-base outline-none"
		/>
		{#if query}
			<button
				type="button"
				onclick={clearQuery}
				aria-label="Clear search"
				class="text-base-content/40 hover:text-base-content shrink-0 transition-colors"
			>
				<XIcon size={18} />
			</button>
		{/if}
	</div>

	<!-- Results -->
	{#if !query.trim()}
		<div class="text-base-content/30 flex flex-col items-center gap-3 py-24 text-center">
			<MagnifyingGlassIcon size={48} class="opacity-20" />
			<p>Type to search.</p>
		</div>
	{:else if resultCount === 0 && !loading}
		<div class="text-base-content/40 flex flex-col items-center gap-3 py-24 text-center">
			<p>No results for "{query}".</p>
		</div>
	{:else}
		<div class="grid grid-cols-2 gap-4 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-6">
			{#each results as result (result.id)}
				<button
					type="button"
					onclick={() => goto(detailPath(result))}
					class="group flex flex-col gap-2 text-left"
				>
					<div class="bg-base-300 relative aspect-[2/3] w-full overflow-hidden rounded-xl">
						{#if result.posterUrl}
							<img
								src={result.posterUrl}
								alt={result.title}
								loading="lazy"
								class="h-full w-full object-cover transition-transform group-hover:scale-105"
							/>
						{:else}
							<div class="flex h-full w-full items-center justify-center">
								<span class="text-5xl opacity-10">🎬</span>
							</div>
						{/if}
						<span
							class="bg-base-100/80 text-base-content/70 absolute top-2 right-2 rounded-full px-2 py-0.5 text-[0.65rem] font-medium uppercase"
						>
							{kindLabel(result)}
						</span>
					</div>
					<div class="px-0.5">
						<p class="line-clamp-1 text-sm font-medium">{result.title}</p>
						{#if result.subtitle}
							<p class="text-base-content/40 text-xs">{result.subtitle}</p>
						{/if}
					</div>
				</button>
			{/each}
		</div>
	{/if}
</div>
