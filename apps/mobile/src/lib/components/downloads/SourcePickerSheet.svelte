<script lang="ts">
	// Asks which source to download from: "Best available" (the backend tries
	// the top links) or one specific link. Links arrive live as plugins answer.
	import SparkleIcon from 'phosphor-svelte/lib/SparkleIcon';
	import SealCheckIcon from 'phosphor-svelte/lib/SealCheckIcon';
	import { formatSpeed } from '@neoworks-dev/otter-app/tmdb';
	import { rankStreams, subscribeStreams, type Stream } from '@neoworks-dev/otter-app/streams';
	import BottomSheet from '$lib/components/BottomSheet.svelte';

	interface Props {
		mediaId: string;
		title: string;
		/** streamId undefined means "Best available". */
		onpick: (streamId: string | undefined) => void;
		onclose: () => void;
	}

	let { mediaId, title, onpick, onclose }: Props = $props();

	let streams = $state<Stream[]>([]);
	let searching = $state(true);

	$effect(() => {
		const id = mediaId;
		streams = [];
		searching = true;
		return subscribeStreams(id, (update) => {
			streams = rankStreams(update.streams);
			searching = !update.done;
		});
	});

	function streamLabel(stream: Stream): string {
		if (stream.label) return stream.label;
		return 'Link';
	}

	function details(stream: Stream): string {
		const parts: string[] = [];
		if (stream.quality) parts.push(stream.quality);
		if (stream.language) parts.push(stream.language);
		const speed = formatSpeed(stream.speedBps);
		if (speed) parts.push(`~${speed}`);
		return parts.join(' · ');
	}
</script>

<BottomSheet {onclose}>
	<div class="flex flex-col gap-4">
		<div class="flex flex-col">
			<h2 class="text-lg font-bold">Download from</h2>
			<p class="line-clamp-1 text-sm text-white/55">{title}</p>
		</div>

		<div class="flex flex-col divide-y divide-white/10 rounded-3xl bg-white/6 px-4">
			<button class="flex items-center gap-3 py-3.5 text-left" onclick={() => onpick(undefined)}>
				<span class="flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-white/10">
					<SparkleIcon size={18} weight="fill" />
				</span>
				<span class="flex flex-1 flex-col">
					<span class="text-sm font-semibold">Best available</span>
					<span class="text-xs text-white/50">Tries the top sources until one works</span>
				</span>
			</button>
			{#each streams as stream (stream.id)}
				<button class="flex items-center gap-3 py-3 text-left" onclick={() => onpick(stream.id)}>
					<span class="flex min-w-0 flex-1 flex-col">
						<span class="flex items-center gap-1.5 text-sm font-semibold">
							<span class="truncate">{streamLabel(stream)}</span>
							{#if stream.playable}
								<SealCheckIcon size={14} weight="fill" class="text-success shrink-0" />
							{/if}
						</span>
						{#if details(stream)}
							<span class="truncate text-xs text-white/50">{details(stream)}</span>
						{/if}
					</span>
					<span
						class="shrink-0 rounded-full bg-white/10 px-2 py-0.5 text-[11px] font-medium text-white/70"
					>
						{stream.plugin}
					</span>
				</button>
			{/each}
		</div>

		{#if searching}
			<p class="flex items-center justify-center gap-2 text-sm text-white/55">
				<span class="loading loading-spinner loading-xs"></span>
				Searching sites…
			</p>
		{:else if streams.length === 0}
			<p class="text-center text-sm text-white/55">No sources found yet.</p>
		{/if}
	</div>
</BottomSheet>
