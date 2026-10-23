<script lang="ts">
	import { goto } from '$app/navigation';
	import MediaCard from '$lib/components/MediaCard.svelte';
	import { activeProfile } from '@neoworks-dev/otter-app/profile';
	import { fetchHistory, detailPath, type RailItem } from '@neoworks-dev/otter-app/discovery';

	let items = $state<RailItem[]>([]);
	let loaded = $state(false);

	$effect(() => {
		const profile = $activeProfile;
		if (!profile) return;
		let cancelled = false;
		fetchHistory(profile.id).then((result) => {
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
</script>

<div class="flex flex-col gap-6">
	<h1 class="text-3xl font-bold">History</h1>

	{#if loaded && items.length === 0}
		<div class="flex flex-col items-center gap-4 py-24 text-center">
			<span class="text-6xl opacity-10">🕑</span>
			<p class="text-base-content/40">Nothing watched yet</p>
		</div>
	{:else}
		<div class="grid grid-cols-2 gap-4 sm:grid-cols-3 md:grid-cols-4 xl:grid-cols-5 2xl:grid-cols-6">
			{#each items as item, index (`${index}-${item.id}`)}
				<MediaCard {item} onclick={() => open(item)} />
			{/each}
		</div>
	{/if}
</div>
