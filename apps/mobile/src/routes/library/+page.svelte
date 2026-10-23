<script lang="ts">
	import { goto } from '$app/navigation';
	import MediaRail from '$lib/components/MediaRail.svelte';
	import { detailPath, type RailItem } from '@neoworks-dev/otter-app/discovery';

	let { data } = $props();

	type SectionRef = { focus: () => void; focusFirst: () => void };
	let moviesRail = $state<SectionRef>();
	let seriesRail = $state<SectionRef>();

	let isEmpty = $derived(data.movies.length === 0 && data.series.length === 0);

	function openItem(item: RailItem) {
		goto(detailPath(item));
	}

	function moveFromMovies(direction: 'up' | 'down') {
		if (direction === 'down') seriesRail?.focusFirst();
	}

	function moveFromSeries(direction: 'up' | 'down') {
		if (direction === 'up') moviesRail?.focusFirst();
	}
</script>

<div class="flex flex-col gap-8">
	<h1 class="text-3xl font-bold">Library</h1>

	{#if isEmpty}
		<div class="flex flex-col items-center gap-4 py-24 text-center">
			<span class="text-6xl opacity-10">🎞️</span>
			<p class="text-base-content/40">Your library is empty</p>
			<p class="text-base-content/30 text-sm">
				Titles you search for, open or get recommended show up here.
			</p>
		</div>
	{:else}
		<MediaRail
			bind:this={moviesRail}
			title="Movies"
			items={data.movies}
			seeAll={{ href: '/movies/all', label: 'All movies' }}
			onselect={openItem}
			onedge={moveFromMovies}
		/>
		<MediaRail
			bind:this={seriesRail}
			title="TV Shows"
			items={data.series}
			seeAll={{ href: '/series/all', label: 'All TV shows' }}
			onselect={openItem}
			onedge={moveFromSeries}
		/>
	{/if}
</div>
