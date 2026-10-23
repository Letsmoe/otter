<script lang="ts">
	// Starts (after asking for the source), shows and retries the download of
	// a movie or episode. "labeled" is the title page's icon-over-label button,
	// "icon" the episode row's.
	import { goto } from '$app/navigation';
	import { notifications } from '$lib/stores/notifications';
	import { downloads } from '$lib/stores/downloads.svelte';
	import DownloadState from './DownloadState.svelte';
	import SourcePickerSheet from './SourcePickerSheet.svelte';

	interface Props {
		mediaId: string;
		/** Shown in the source picker, e.g. "Heat" or "S1:E3 Pilot". */
		title: string;
		variant?: 'labeled' | 'icon';
	}

	let { mediaId, title, variant = 'labeled' }: Props = $props();

	let picking = $state(false);

	let item = $derived(downloads.forMedia(mediaId));

	function label(): string {
		if (!item) return 'Download';
		if (item.status === 'DONE') return 'Downloaded';
		if (item.status === 'FAILED') return 'Retry';
		if (item.status === 'QUEUED') return 'Waiting';
		return `${Math.round(item.progress * 100)}%`;
	}

	function onclick() {
		if (item && item.status !== 'FAILED') {
			goto('/downloads');
			return;
		}
		picking = true;
	}

	async function start(streamId: string | undefined) {
		picking = false;
		try {
			await downloads.start(mediaId, streamId);
		} catch (error) {
			notifications.push(errorMessage(error), 'error');
		}
	}

	function errorMessage(error: unknown): string {
		if (error instanceof Error && error.message) return error.message;
		return 'Could not start the download';
	}
</script>

{#if picking}
	<SourcePickerSheet {mediaId} {title} onpick={start} onclose={() => (picking = false)} />
{/if}

{#if variant === 'labeled'}
	<button class="flex w-20 flex-col items-center gap-1.5 text-[11px] text-white/70" {onclick}>
		<span class="text-white"><DownloadState {item} /></span>
		{label()}
	</button>
{:else}
	<button
		class="flex h-10 w-10 shrink-0 items-center justify-center text-white/80"
		aria-label={label()}
		{onclick}
	>
		<DownloadState {item} size={22} />
	</button>
{/if}
