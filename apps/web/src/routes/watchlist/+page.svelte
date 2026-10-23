<script lang="ts">
	import { get } from 'svelte/store';
	import { goto } from '$app/navigation';
	import MediaCard from '$lib/components/MediaCard.svelte';
	import { activeProfile } from '@neoworks-dev/otter-app/profile';
	import { fetchWatchlist, removeFromWatchlist, detailPath, type RailItem } from '@neoworks-dev/otter-app/discovery';

	let items = $state<RailItem[]>([]);
	let loaded = $state(false);

	$effect(() => {
		const profile = $activeProfile;
		if (!profile) return;
		let cancelled = false;
		fetchWatchlist(profile.id).then((result) => {
			if (cancelled) return;
			items = result;
			loaded = true;
		});
		return () => {
			cancelled = true;
		};
	});

	function open(item: RailItem) {
		goto(detailPath(item));
	}

	async function remove(item: RailItem) {
		const profile = get(activeProfile);
		if (!profile) return;
		await removeFromWatchlist(profile.id, item.id);
		items = items.filter((i) => i.id !== item.id);
	}
</script>

<div class="flex flex-col gap-6">
	<h1 class="text-3xl font-bold">Watchlist</h1>

	{#if loaded && items.length === 0}
		<div class="flex flex-col items-center gap-4 py-24 text-center">
			<span class="text-6xl opacity-10">🔖</span>
			<p class="text-base-content/40">Nothing saved yet</p>
		</div>
	{:else}
		<div class="grid grid-cols-2 gap-4 sm:grid-cols-3 md:grid-cols-4 xl:grid-cols-5 2xl:grid-cols-6">
			{#each items as item (item.id)}
				<div class="group relative">
					<MediaCard
						item={{ id: item.id, title: item.title, posterUrl: item.posterUrl }}
						onclick={() => open(item)}
					/>
					<button
						type="button"
						class="btn btn-error btn-xs absolute right-2 top-2 opacity-0 transition-opacity group-hover:opacity-100"
						onclick={() => remove(item)}
						title="Remove from watchlist"
					>
						✕
					</button>
				</div>
			{/each}
		</div>
	{/if}
</div>
