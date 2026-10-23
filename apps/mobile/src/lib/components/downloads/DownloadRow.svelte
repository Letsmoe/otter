<script lang="ts">
	// A downloaded movie or episode: thumbnail (tap to play once done), name,
	// size or status, and the state icon — or a delete button while editing.
	import PlayIcon from 'phosphor-svelte/lib/PlayIcon';
	import XIcon from 'phosphor-svelte/lib/XIcon';
	import { formatSize, type DownloadItem } from '@neoworks-dev/otter-app/downloads';
	import DownloadState from './DownloadState.svelte';

	interface Props {
		item: DownloadItem;
		editing: boolean;
		onplay: (item: DownloadItem) => void;
		onretry: (item: DownloadItem) => void;
		ondelete: (item: DownloadItem) => void;
	}

	let { item, editing, onplay, onretry, ondelete }: Props = $props();

	let playable = $derived(item.status === 'DONE' && item.url !== null);

	function name(): string {
		if (!item.episode) return item.titleName;
		const episodeName = item.episode.name || `Episode ${item.episode.episodeNumber}`;
		return `${item.episode.episodeNumber}. ${episodeName}`;
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

	function onstate() {
		if (item.status === 'FAILED') onretry(item);
	}
</script>

<div class="flex items-center gap-3">
	<button
		class="relative aspect-video w-36 shrink-0 overflow-hidden rounded-md bg-white/5"
		aria-label="Play {name()}"
		disabled={!playable}
		onclick={() => onplay(item)}
	>
		{#if item.imageUrl}
			<img src={item.imageUrl} alt="" loading="lazy" class="h-full w-full object-cover" />
		{/if}
		{#if playable}
			<span
				class="glass-bar absolute inset-0 m-auto flex h-10 w-10 items-center justify-center rounded-full"
			>
				<PlayIcon size={18} weight="fill" />
			</span>
		{/if}
	</button>
	<div class="flex min-w-0 flex-1 flex-col">
		<span class="line-clamp-2 text-sm font-semibold">{name()}</span>
		<span class="line-clamp-2 text-xs text-white/55" class:text-error={item.status === 'FAILED'}>
			{statusLine()}
		</span>
	</div>
	{#if editing}
		<button
			class="glass-bar flex h-9 w-9 shrink-0 items-center justify-center rounded-full"
			aria-label="Delete download"
			onclick={() => ondelete(item)}
		>
			<XIcon size={16} weight="bold" />
		</button>
	{:else}
		<button
			class="flex h-10 w-10 shrink-0 items-center justify-center"
			aria-label="Download state"
			onclick={onstate}
		>
			<DownloadState {item} size={24} />
		</button>
	{/if}
</div>
