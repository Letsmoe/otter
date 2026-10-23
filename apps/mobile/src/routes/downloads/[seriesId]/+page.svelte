<script lang="ts">
	// One series' downloaded episodes, grouped by season.
	import { page } from '$app/state';
	import type { DownloadItem } from '@neoworks-dev/otter-app/downloads';
	import { downloads } from '$lib/stores/downloads.svelte';
	import ScreenHeader from '$lib/components/ScreenHeader.svelte';
	import DownloadRow from '$lib/components/downloads/DownloadRow.svelte';
	import DownloadPlayer from '$lib/components/downloads/DownloadPlayer.svelte';

	let editing = $state(false);
	let playingItem = $state<DownloadItem | null>(null);

	let episodes = $derived(downloads.items.filter((item) => item.titleId === page.params.seriesId));
	let seriesName = $derived(nameOf(episodes));
	let seasons = $derived(
		[...new Set(episodes.map(seasonOf))].sort((first, second) => first - second)
	);

	function nameOf(items: DownloadItem[]): string {
		if (items.length === 0) return 'Downloads';
		return items[0].titleName;
	}

	function seasonOf(item: DownloadItem): number {
		if (!item.episode) return 0;
		return item.episode.seasonNumber;
	}

	function episodeNumberOf(item: DownloadItem): number {
		if (!item.episode) return 0;
		return item.episode.episodeNumber;
	}

	function episodesOf(seasonNumber: number): DownloadItem[] {
		const inSeason = episodes.filter((item) => seasonOf(item) === seasonNumber);
		return inSeason.sort((first, second) => episodeNumberOf(first) - episodeNumberOf(second));
	}

	function editLabel(): string {
		if (editing) return 'Done';
		return 'Edit';
	}
</script>

{#if playingItem}
	<DownloadPlayer item={playingItem} onclose={() => (playingItem = null)} />
{/if}

<div class="-mx-3">
	<ScreenHeader
		title={seriesName}
		onback={() => history.back()}
		actionLabel={editLabel()}
		onaction={() => (editing = !editing)}
	/>
</div>

<div class="flex flex-col gap-6 pb-6">
	{#each seasons as seasonNumber (seasonNumber)}
		<section class="flex flex-col gap-3">
			<h2 class="text-sm font-semibold text-white/70">Season {seasonNumber}</h2>
			{#each episodesOf(seasonNumber) as item (item.id)}
				<DownloadRow
					{item}
					{editing}
					onplay={(chosen) => (playingItem = chosen)}
					onretry={(chosen) => downloads.start(chosen.mediaId)}
					ondelete={(chosen) => downloads.remove(chosen.id)}
				/>
			{/each}
		</section>
	{:else}
		<p class="pt-16 text-center text-sm text-white/50">No downloaded episodes.</p>
	{/each}
</div>
