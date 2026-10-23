<script lang="ts">
	import PlayIcon from 'phosphor-svelte/lib/PlayIcon';
	import ArrowClockwiseIcon from 'phosphor-svelte/lib/ArrowClockwiseIcon';
	import SealCheckIcon from 'phosphor-svelte/lib/SealCheckIcon';
	import FocusList from './FocusList.svelte';
	import VideoPlayer from './VideoPlayer.svelte';
	import { formatSpeed } from '@neoworks-dev/otter-app/tmdb';
	import { notifications } from '$lib/stores/notifications';
	import {
		subscribeStreams,
		findStreams,
		resolveStream,
		rankStreams,
		type Stream
	} from '@neoworks-dev/otter-app/streams';

	interface Props {
		// Movie or episode id (movie:… / episode:…); also used for progress tracking.
		mediaId: string;
		title: string;
		// Edge callback from the internal FocusList.
		onedge?: (direction: 'left' | 'right' | 'up' | 'down') => void;
	}

	let { mediaId, title, onedge }: Props = $props();

	let streams = $state<Stream[]>([]);
	let searching = $state(true);

	// Stored links arrive first, then updates as plugins answer.
	$effect(() => {
		const id = mediaId;
		streams = [];
		searching = true;
		showAlternatives = false;
		return subscribeStreams(id, (update) => {
			streams = rankStreams(update.streams);
			searching = !update.done;
		});
	});

	// One FocusList entry per interactive control, so the whole sources row is
	// arrow-key navigable: Play, the more-sources toggle, search again, then each
	// alternative source once expanded.
	type SourceEntry =
		| { kind: 'play'; stream: Stream }
		| { kind: 'toggle' }
		| { kind: 'refresh' }
		| { kind: 'alternative'; stream: Stream };

	let best = $derived<Stream | undefined>(streams[0]);
	let alternatives = $derived(streams.slice(1));
	let showAlternatives = $state(false);

	let entries = $derived.by<SourceEntry[]>(() => {
		const list: SourceEntry[] = [];
		if (best) list.push({ kind: 'play', stream: best });
		if (alternatives.length > 0) list.push({ kind: 'toggle' });
		list.push({ kind: 'refresh' });
		if (!showAlternatives) return list;
		for (const stream of alternatives) list.push({ kind: 'alternative', stream });
		return list;
	});

	let focusList = $state<{ focus: () => void; focusFirst: () => void }>();

	/** Enter the sources row at the Play button. */
	export function focusFirst() {
		focusList?.focusFirst();
	}

	export function focus() {
		focusList?.focus();
	}

	function activate(entry: SourceEntry) {
		if (entry.kind === 'toggle') {
			showAlternatives = !showAlternatives;
			return;
		}
		if (entry.kind === 'refresh') {
			searchAgain();
			return;
		}
		play(entry.stream);
	}

	let resolving = $state<string | null>(null);
	let activeStream = $state<{ src: string; mimeType: string; title: string } | null>(null);

	function streamLabel(stream: Stream): string {
		if (stream.label) return stream.label;
		return stream.plugin;
	}

	function playButtonLabel(stream: Stream): string {
		if (resolving === stream.id) return 'Loading…';
		return 'Play';
	}

	function alternativeLabel(stream: Stream): string {
		if (resolving === stream.id) return 'Loading…';
		return streamLabel(stream);
	}

	function toggleLabel(): string {
		if (showAlternatives) return 'Hide sources';
		return `More sources (${alternatives.length})`;
	}

	async function play(stream: Stream) {
		resolving = stream.id;
		try {
			const resolved = await resolveStream(stream.id);
			activeStream = {
				src: resolved.url,
				mimeType: resolved.mimeType,
				title: `${title} – ${streamLabel(stream)}`
			};
		} catch {
			notifications.push('Could not resolve stream', 'error');
		} finally {
			resolving = null;
		}
	}

	async function searchAgain() {
		if (searching) return;
		searching = true;
		try {
			await findStreams(mediaId);
		} catch {
			searching = false;
			notifications.push('Could not start a source lookup', 'error');
		}
	}
</script>

{#if activeStream}
	<VideoPlayer
		src={activeStream.src}
		mimeType={activeStream.mimeType}
		title={activeStream.title}
		{mediaId}
		onclose={() => (activeStream = null)}
	/>
{/if}

<div class="flex flex-col gap-3">
	<FocusList
		bind:this={focusList}
		items={entries}
		orientation="horizontal"
		ariaLabel="Sources"
		class="flex-wrap"
		scroll="top"
		onselect={(entry) => activate(entry)}
		{onedge}
	>
		{#snippet item(entry)}
			{#if entry.kind === 'play'}
				<span
					class="flex items-center gap-2 rounded-lg bg-white px-5 py-2 text-sm font-semibold text-black"
				>
					<PlayIcon size={14} weight="fill" />
					{playButtonLabel(entry.stream)}
				</span>
			{:else if entry.kind === 'toggle'}
				<span
					class="text-base-content/50 hover:text-base-content flex h-full items-center px-1 text-xs transition-colors"
				>
					{toggleLabel()}
				</span>
			{:else if entry.kind === 'refresh'}
				<span
					class="flex items-center gap-2 rounded-lg bg-white/10 px-3 py-2 text-xs text-white transition-colors hover:bg-white/20"
					title="Ask every plugin again"
				>
					<ArrowClockwiseIcon size={14} class={searching ? 'animate-spin' : ''} />
					{#if searching}Searching…{:else}Search again{/if}
				</span>
			{:else}
				<span
					class="flex items-center gap-1.5 rounded-full bg-white/10 px-3 py-1.5 text-xs text-white"
					title="Play from {streamLabel(entry.stream)}"
				>
					<PlayIcon size={11} weight="fill" />
					<span class="font-medium">{alternativeLabel(entry.stream)}</span>
					{#if entry.stream.playable}
						<SealCheckIcon size={12} weight="fill" class="text-success" />
					{/if}
					{#if entry.stream.quality}<span class="opacity-60">{entry.stream.quality}</span>{/if}
					{#if entry.stream.language}<span class="opacity-60">{entry.stream.language}</span>{/if}
					{#if formatSpeed(entry.stream.speedBps)}
						<span class="text-success">~{formatSpeed(entry.stream.speedBps)}</span>
					{/if}
				</span>
			{/if}
		{/snippet}
	</FocusList>

	{#if best}
		<span class="text-base-content/50 flex items-center gap-1.5 text-xs">
			{streamLabel(best)}
			{#if best.playable}
				<SealCheckIcon size={13} weight="fill" class="text-success" />
			{/if}
			{#if best.quality}<span>{best.quality}</span>{/if}
			{#if best.language}<span>{best.language}</span>{/if}
			{#if formatSpeed(best.speedBps)}
				<span class="text-success">~{formatSpeed(best.speedBps)}</span>
			{/if}
		</span>
	{:else if searching}
		<p class="text-base-content/40 text-sm">Searching sources…</p>
	{:else}
		<p class="text-base-content/40 text-sm">No sources found.</p>
	{/if}
</div>
