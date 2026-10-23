<script lang="ts">
	// Icon for a download's state: arrow (none), waiting ring, progress ring,
	// check (done) or warning (failed).
	import DownloadSimpleIcon from 'phosphor-svelte/lib/DownloadSimpleIcon';
	import CheckCircleIcon from 'phosphor-svelte/lib/CheckCircleIcon';
	import WarningCircleIcon from 'phosphor-svelte/lib/WarningCircleIcon';
	import type { DownloadItem } from '@neoworks-dev/otter-app/downloads';

	const RING_RADIUS = 10;
	const RING_LENGTH = 2 * Math.PI * RING_RADIUS;

	interface Props {
		item: DownloadItem | undefined;
		size?: number;
	}

	let { item, size = 26 }: Props = $props();

	let dashOffset = $derived(RING_LENGTH * (1 - progressOf(item)));

	function progressOf(download: DownloadItem | undefined): number {
		if (!download || download.status !== 'DOWNLOADING') return 0;
		return Math.min(1, Math.max(0, download.progress));
	}
</script>

{#if !item}
	<DownloadSimpleIcon {size} />
{:else if item.status === 'DONE'}
	<CheckCircleIcon {size} weight="fill" class="text-success" />
{:else if item.status === 'FAILED'}
	<WarningCircleIcon {size} class="text-error" />
{:else}
	<svg viewBox="0 0 24 24" width={size} height={size} class="-rotate-90" aria-hidden="true">
		<circle
			cx="12"
			cy="12"
			r={RING_RADIUS}
			fill="none"
			stroke="currentColor"
			stroke-opacity="0.25"
			stroke-width="2"
		/>
		<circle
			cx="12"
			cy="12"
			r={RING_RADIUS}
			fill="none"
			stroke="currentColor"
			stroke-width="2"
			stroke-linecap="round"
			stroke-dasharray={RING_LENGTH}
			stroke-dashoffset={dashOffset}
			class="transition-[stroke-dashoffset] duration-500"
			class:ring-waiting={item.status === 'QUEUED'}
		/>
	</svg>
{/if}

<style>
	/* Waiting: a short arc circles until the download starts. */
	.ring-waiting {
		stroke-dashoffset: 52;
		transform-origin: center;
		animation: ring-spin 1.2s linear infinite;
	}

	@keyframes ring-spin {
		to {
			rotate: 360deg;
		}
	}
</style>
