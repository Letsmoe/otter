<script lang="ts">
	// Netflix's Downloads: the profile, then one row per movie and per series
	// (episodes behind a chevron), an edit mode to delete, and a way to find more.
	import PencilSimpleIcon from 'phosphor-svelte/lib/PencilSimpleIcon';
	import CaretRightIcon from 'phosphor-svelte/lib/CaretRightIcon';
	import DownloadSimpleIcon from 'phosphor-svelte/lib/DownloadSimpleIcon';
	import { activeProfile, avatarFor } from '@neoworks-dev/otter-app/profile';
	import {
		groupDownloads,
		type DownloadGroup,
		type DownloadItem
	} from '@neoworks-dev/otter-app/downloads';
	import { downloads } from '$lib/stores/downloads.svelte';
	import DownloadRow from '$lib/components/downloads/DownloadRow.svelte';
	import DownloadPlayer from '$lib/components/downloads/DownloadPlayer.svelte';

	let editing = $state(false);
	let playingItem = $state<DownloadItem | null>(null);

	let groups = $derived(groupDownloads(downloads.items));

	function isSeries(group: DownloadGroup): boolean {
		return group.titleId.startsWith('series:');
	}

	function episodeCount(group: DownloadGroup): string {
		if (group.items.length === 1) return '1 Episode';
		return `${group.items.length} Episodes`;
	}

	// Summarises the series' episodes: still running ones first.
	function groupStatus(group: DownloadGroup): string {
		const running = group.items.filter(
			(item) => item.status === 'DOWNLOADING' || item.status === 'QUEUED'
		);
		const failed = group.items.filter((item) => item.status === 'FAILED');
		if (running.length > 0) return `Downloading ${running.length}`;
		if (failed.length > 0) return `${failed.length} failed`;
		return 'Downloaded';
	}

	function retry(item: DownloadItem) {
		downloads.start(item.mediaId);
	}

	function remove(item: DownloadItem) {
		downloads.remove(item.id);
	}
</script>

{#if playingItem}
	<DownloadPlayer item={playingItem} onclose={() => (playingItem = null)} />
{/if}


{#if $activeProfile && downloads.items.length > 0}
	<div class="flex items-center gap-3 pt-4 pb-4">
		<img src={avatarFor($activeProfile)} alt="" class="h-8 w-8 rounded-md" />
		<span class="flex-1 text-lg font-bold">{$activeProfile.name}</span>
		<button
			class="flex h-10 w-10 items-center justify-center rounded-full"
			class:glass-bar={editing}
			aria-label="Edit downloads"
			aria-pressed={editing}
			onclick={() => (editing = !editing)}
		>
			<PencilSimpleIcon size={22} />
		</button>
	</div>
{/if}

{#if downloads.items.length > 0}
	<div class="flex flex-col gap-4">
		{#each groups as group (group.titleId)}
			{#if isSeries(group)}
				<a href="/downloads/{group.titleId}" class="flex items-center gap-3">
					<span class="aspect-video w-36 shrink-0 overflow-hidden rounded-md bg-white/5">
						{#if group.imageUrl}
							<img src={group.imageUrl} alt="" loading="lazy" class="h-full w-full object-cover" />
						{/if}
					</span>
					<span class="flex min-w-0 flex-1 flex-col">
						<span class="line-clamp-2 text-sm font-semibold">{group.titleName}</span>
						<span class="text-xs text-white/55">{episodeCount(group)}</span>
						<span class="text-xs text-white/55">{groupStatus(group)}</span>
					</span>
					<CaretRightIcon size={22} class="shrink-0" />
				</a>
			{:else}
				<DownloadRow
					item={group.items[0]}
					{editing}
					onplay={(item) => (playingItem = item)}
					onretry={retry}
					ondelete={remove}
				/>
			{/if}
		{/each}
	</div>
{:else if downloads.loaded}
	<div class="flex flex-col items-center gap-3 pt-16 text-center">
		<DownloadSimpleIcon size={56} class="opacity-40" />
		<p class="font-semibold">No downloads yet</p>
		<p class="max-w-xs text-sm opacity-60">
			Movies and episodes you download play here, even offline.
		</p>
	</div>
{/if}

<div class="flex justify-center pt-10 pb-6">
	<a href="/search" class="glass-bar rounded-full px-6 py-3 text-center text-sm font-bold">
		See What You Can Download
	</a>
</div>
