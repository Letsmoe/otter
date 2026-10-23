<script lang="ts">
	// Every stream link found for the title, best first; tapping one plays it.
	import PlayIcon from 'phosphor-svelte/lib/PlayIcon';
	import SealCheckIcon from 'phosphor-svelte/lib/SealCheckIcon';
	import ArrowClockwiseIcon from 'phosphor-svelte/lib/ArrowClockwiseIcon';
	import { formatSpeed } from '@neoworks-dev/otter-app/tmdb';
	import type { Stream } from '@neoworks-dev/otter-app/streams';

	interface Props {
		streams: Stream[];
		searching: boolean;
		loadingId: string | null;
		onplay: (stream: Stream) => void;
		onsearchagain: () => void;
	}

	let { streams, searching, loadingId, onplay, onsearchagain }: Props = $props();

	function streamLabel(stream: Stream): string {
		if (stream.label) return stream.label;
		return 'Link';
	}
</script>

<div class="flex flex-col gap-2">
	{#each streams as stream (stream.id)}
		<button
			class="flex items-center gap-3 rounded-md bg-white/8 px-3 py-3 text-left"
			onclick={() => onplay(stream)}
		>
			<span class="flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-white/10">
				{#if loadingId === stream.id}
					<span class="loading loading-spinner loading-sm"></span>
				{:else}
					<PlayIcon size={16} weight="fill" />
				{/if}
			</span>
			<span class="flex min-w-0 flex-1 flex-col">
				<span class="flex items-center gap-1.5 truncate text-sm font-semibold">
					<span class="truncate">{streamLabel(stream)}</span>
					{#if stream.playable}<SealCheckIcon
							size={14}
							weight="fill"
							class="text-success shrink-0"
						/>{/if}
				</span>
				<span class="flex gap-2 text-xs text-white/50">
					{#if stream.quality}<span>{stream.quality}</span>{/if}
					{#if stream.language}<span>{stream.language}</span>{/if}
					{#if formatSpeed(stream.speedBps)}<span>~{formatSpeed(stream.speedBps)}</span>{/if}
				</span>
			</span>
			<span
				class="shrink-0 rounded-full bg-white/10 px-2 py-0.5 text-[11px] font-medium text-white/70"
			>
				{stream.plugin}
			</span>
		</button>
	{:else}
		{#if !searching}
			<p class="py-6 text-center text-sm text-white/50">No sources found yet.</p>
		{/if}
	{/each}

	<button
		class="mt-1 flex items-center justify-center gap-2 py-3 text-sm text-white/70"
		disabled={searching}
		onclick={onsearchagain}
	>
		<ArrowClockwiseIcon size={16} class={searching ? 'animate-spin' : ''} />
		{#if searching}Searching sites…{:else}Search again{/if}
	</button>
</div>
