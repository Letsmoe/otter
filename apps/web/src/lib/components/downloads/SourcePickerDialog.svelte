<script lang="ts">
	// Asks which source to download from: "Best available" (the backend tries
	// the top links) or one specific link. Links arrive live as plugins answer.
	import SparkleIcon from 'phosphor-svelte/lib/SparkleIcon';
	import SealCheckIcon from 'phosphor-svelte/lib/SealCheckIcon';
	import XIcon from 'phosphor-svelte/lib/XIcon';
	import { formatSpeed } from '@neoworks-dev/otter-app/tmdb';
	import { rankStreams, subscribeStreams, type Stream } from '@neoworks-dev/otter-app/streams';

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

	function onWindowKeydown(event: KeyboardEvent) {
		if (event.key === 'Escape') onclose();
	}
</script>

<svelte:window onkeydown={onWindowKeydown} />

<div class="fixed inset-0 z-50 flex items-center justify-center p-6">
	<button class="absolute inset-0 bg-black/60" aria-label="Close" onclick={onclose}></button>

	<div
		class="border-base-300 bg-base-200 relative flex max-h-[80vh] w-full max-w-lg flex-col gap-4 rounded-xl border p-5 shadow-2xl"
		role="dialog"
		aria-modal="true"
		aria-label="Download from"
	>
		<div class="flex items-start gap-3">
			<div class="flex min-w-0 flex-1 flex-col">
				<h2 class="text-lg font-semibold">Download from</h2>
				<p class="text-base-content/55 truncate text-sm">{title}</p>
			</div>
			<button
				class="text-base-content/60 hover:bg-base-300 hover:text-base-content flex h-8 w-8 items-center justify-center rounded-md transition-colors"
				aria-label="Close"
				onclick={onclose}
			>
				<XIcon size={16} />
			</button>
		</div>

		<div class="no-scrollbar flex flex-col gap-1 overflow-y-auto">
			<button
				class="hover:bg-base-300 flex items-center gap-3 rounded-lg px-3 py-2.5 text-left transition-colors"
				onclick={() => onpick(undefined)}
			>
				<span class="flex h-8 w-8 shrink-0 items-center justify-center rounded-full bg-white/10">
					<SparkleIcon size={16} weight="fill" />
				</span>
				<span class="flex flex-1 flex-col">
					<span class="text-sm font-semibold">Best available</span>
					<span class="text-base-content/50 text-xs">Tries the top sources until one works</span>
				</span>
			</button>
			{#each streams as stream (stream.id)}
				<button
					class="hover:bg-base-300 flex items-center gap-3 rounded-lg px-3 py-2.5 text-left transition-colors"
					onclick={() => onpick(stream.id)}
				>
					<span class="flex min-w-0 flex-1 flex-col">
						<span class="flex items-center gap-1.5 text-sm font-semibold">
							<span class="truncate">{streamLabel(stream)}</span>
							{#if stream.playable}
								<SealCheckIcon size={14} weight="fill" class="text-success shrink-0" />
							{/if}
						</span>
						{#if details(stream)}
							<span class="text-base-content/50 truncate text-xs">{details(stream)}</span>
						{/if}
					</span>
					<span
						class="text-base-content/70 shrink-0 rounded-full bg-white/10 px-2 py-0.5 text-[11px] font-medium"
					>
						{stream.plugin}
					</span>
				</button>
			{/each}
		</div>

		{#if searching}
			<p class="text-base-content/55 flex items-center justify-center gap-2 text-sm">
				<span class="loading loading-spinner loading-xs"></span>
				Searching sites…
			</p>
		{:else if streams.length === 0}
			<p class="text-base-content/55 text-center text-sm">No sources found yet.</p>
		{/if}
	</div>
</div>
