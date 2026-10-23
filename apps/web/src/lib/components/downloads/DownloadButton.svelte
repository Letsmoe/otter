<script lang="ts">
	// Starts (after asking for the source), shows and retries the server-side
	// download of a movie or episode. A running or finished one opens /downloads.
	import { goto } from '$app/navigation';
	import { notifications } from '$lib/stores/notifications';
	import { downloads } from '$lib/stores/downloads.svelte';
	import DownloadState from './DownloadState.svelte';
	import SourcePickerDialog from './SourcePickerDialog.svelte';

	interface Props {
		mediaId: string;
		/** Shown in the source picker, e.g. "Heat" or "S1:E3 Pilot". */
		title: string;
	}

	let { mediaId, title }: Props = $props();

	let picking = $state(false);

	let item = $derived(downloads.forMedia(mediaId));

	function label(): string {
		if (!item) return 'Download';
		if (item.status === 'DONE') return 'Downloaded';
		if (item.status === 'FAILED') return 'Retry download';
		if (item.status === 'QUEUED') return 'Waiting…';
		return `Downloading ${Math.round(item.progress * 100)}%`;
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
			notifications.push('Download started', 'success');
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
	<SourcePickerDialog {mediaId} {title} onpick={start} onclose={() => (picking = false)} />
{/if}

<button
	type="button"
	{onclick}
	class="flex items-center gap-2 rounded-lg bg-white/10 px-5 py-2.5 text-sm font-semibold text-white transition-colors hover:bg-white/20"
>
	<DownloadState {item} size={16} />
	{label()}
</button>
