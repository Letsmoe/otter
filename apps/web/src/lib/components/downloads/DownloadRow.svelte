<script lang="ts">
	// A downloaded movie or episode: thumbnail (click to play once done), name,
	// size or status, and retry/delete actions.
	import PlayIcon from 'phosphor-svelte/lib/PlayIcon';
	import TrashIcon from 'phosphor-svelte/lib/TrashIcon';
	import ArrowClockwiseIcon from 'phosphor-svelte/lib/ArrowClockwiseIcon';
	import { formatSize, type DownloadItem } from '@neoworks-dev/otter-app/downloads';
	import DownloadState from './DownloadState.svelte';

	interface Props {
		item: DownloadItem;
		onplay: (item: DownloadItem) => void;
		onretry: (item: DownloadItem) => void;
		ondelete: (item: DownloadItem) => void;
	}

	let { item, onplay, onretry, ondelete }: Props = $props();

	let playable = $derived(item.status === 'DONE' && item.url !== null);
	let running = $derived(item.status === 'QUEUED' || item.status === 'DOWNLOADING');
	let progressPercent = $derived(Math.min(100, Math.max(0, item.progress * 100)));

	function name(): string {
		if (!item.episode) return item.titleName;
		const episodeName = item.episode.name || `Episode ${item.episode.episodeNumber}`;
		return `S${item.episode.seasonNumber}:E${item.episode.episodeNumber} · ${episodeName}`;
	}

	function statusLine(): string {
		if (item.status === 'DONE') return formatSize(item.sizeBytes);
		if (item.status === 'QUEUED') return 'Waiting';
		if (item.status === 'FAILED') return item.error || 'Download failed';
		const size = formatSize(item.sizeBytes);
		const percent = `${Math.round(item.progress * 100)}%`;
		if (!size) return `Downloading · ${percent}`;
		return `Downloading · ${percent} · ${size}`;
	}
</script>

<div class="flex items-center gap-4">
	<button
		class="group relative aspect-video w-44 shrink-0 overflow-hidden rounded-lg bg-white/5"
		aria-label="Play {name()}"
		disabled={!playable}
		onclick={() => onplay(item)}
	>
		{#if item.imageUrl}
			<img src={item.imageUrl} alt="" loading="lazy" class="h-full w-full object-cover" />
		{/if}
		{#if playable}
			<span
				class="absolute inset-0 m-auto flex h-10 w-10 items-center justify-center rounded-full bg-white/90 text-black opacity-80 shadow-xl transition-opacity group-hover:opacity-100"
			>
				<PlayIcon size={18} weight="fill" />
			</span>
		{/if}
	</button>
	<div class="flex min-w-0 flex-1 flex-col gap-0.5">
		<span class="line-clamp-1 text-sm font-semibold">{name()}</span>
		<span
			class="text-base-content/55 line-clamp-2 text-xs"
			class:text-error={item.status === 'FAILED'}
		>
			{statusLine()}
		</span>
		{#if running}
			<div class="mt-1.5 h-1.5 w-full max-w-md overflow-hidden rounded-full bg-white/10">
				<div
					class="bg-primary h-full rounded-full transition-[width] duration-500"
					style:width="{progressPercent}%"
				></div>
			</div>
		{/if}
	</div>
	<span class="text-base-content/80 flex h-9 w-9 shrink-0 items-center justify-center">
		<DownloadState {item} size={22} />
	</span>
	{#if item.status === 'FAILED'}
		<button
			class="text-base-content/60 hover:bg-base-300 hover:text-base-content flex h-9 w-9 shrink-0 items-center justify-center rounded-md transition-colors"
			aria-label="Retry download"
			title="Retry"
			onclick={() => onretry(item)}
		>
			<ArrowClockwiseIcon size={18} />
		</button>
	{/if}
	<button
		class="text-base-content/60 hover:bg-base-300 hover:text-error flex h-9 w-9 shrink-0 items-center justify-center rounded-md transition-colors"
		aria-label="Delete download"
		title="Delete"
		onclick={() => ondelete(item)}
	>
		<TrashIcon size={18} />
	</button>
</div>
