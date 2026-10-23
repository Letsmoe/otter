<script lang="ts">
	import FolderOpenIcon from 'phosphor-svelte/lib/FolderOpenIcon';
	import VideoPlayer from '$lib/components/VideoPlayer.svelte';
	import DownloadRow from '$lib/components/downloads/DownloadRow.svelte';
	import { downloads } from '$lib/stores/downloads.svelte';
	import { notifications } from '$lib/stores/notifications';
	import {
		fetchDownloadsFolder,
		groupDownloads,
		openDownloadsFolder,
		type DownloadItem
	} from '@neoworks-dev/otter-app/downloads';

	let groups = $derived(groupDownloads(downloads.items));
	let playing = $state<DownloadItem | null>(null);
	let folder = $state<string | null>(null);

	$effect(() => {
		downloads.refresh();
	});

	$effect(() => {
		fetchDownloadsFolder()
			.then((loaded) => (folder = loaded))
			.catch(() => (folder = null));
	});

	async function openFolder() {
		try {
			await openDownloadsFolder();
		} catch {
			notifications.push(`Could not open ${folder}`, 'error');
		}
	}

	function playerTitle(item: DownloadItem): string {
		if (!item.episode) return item.titleName;
		const code = `S${item.episode.seasonNumber}:E${item.episode.episodeNumber}`;
		return `${item.titleName} · ${code} ${item.episode.name || ''}`.trim();
	}

	async function retry(item: DownloadItem) {
		try {
			await downloads.start(item.mediaId);
		} catch {
			notifications.push('Could not restart the download', 'error');
		}
	}

	async function remove(item: DownloadItem) {
		try {
			await downloads.remove(item.id);
		} catch {
			notifications.push('Could not delete the download', 'error');
		}
	}
</script>

{#if playing}
	<VideoPlayer
		src={playing.url || ''}
		mimeType={playing.mimeType || ''}
		title={playerTitle(playing)}
		mediaId={playing.mediaId}
		onclose={() => (playing = null)}
	/>
{/if}

<div class="flex flex-col gap-6">
	<div class="flex items-center justify-between gap-4">
		<h1 class="text-3xl font-bold">Downloads</h1>
		{#if folder}
			<button
				class="flex items-center gap-2 rounded-md bg-white/10 px-4 py-2 text-sm font-semibold transition-colors hover:bg-white/20"
				title={folder}
				onclick={openFolder}
			>
				<FolderOpenIcon size={18} />
				Open folder
			</button>
		{/if}
	</div>

	{#if downloads.loaded && groups.length === 0}
		<div class="flex flex-col items-center gap-4 py-24 text-center">
			<span class="text-6xl opacity-10">⬇</span>
			<p class="text-base-content/40">Nothing downloaded yet</p>
		</div>
	{:else}
		<div class="flex max-w-4xl flex-col gap-8">
			{#each groups as group (group.titleId)}
				<section class="flex flex-col gap-3">
					{#if group.items[0].episode}
						<h2 class="text-lg font-semibold">{group.titleName}</h2>
					{/if}
					{#each group.items as item (item.id)}
						<DownloadRow
							{item}
							onplay={(selected) => (playing = selected)}
							onretry={retry}
							ondelete={remove}
						/>
					{/each}
				</section>
			{/each}
		</div>
	{/if}
</div>
