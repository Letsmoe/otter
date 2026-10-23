<script lang="ts">
	import { goto } from '$app/navigation';
	import PlayIcon from 'phosphor-svelte/lib/PlayIcon';
	import StreamList from '$lib/components/StreamList.svelte';
	import FocusList from '$lib/components/FocusList.svelte';
	import MediaRail from '$lib/components/MediaRail.svelte';
	import WatchlistButton from '$lib/components/WatchlistButton.svelte';
	import RatingButtons from '$lib/components/RatingButtons.svelte';
	import DownloadButton from '$lib/components/downloads/DownloadButton.svelte';
	import { sidebarFocus } from '$lib/stores/focus';
	import { watchScrollTop } from '$lib/scroll';
	import { posterUrl, backdropUrl, formatRuntime } from '@neoworks-dev/otter-app/tmdb';
	import { detailPath, type RailItem } from '@neoworks-dev/otter-app/discovery';
	import type { CastMember } from '@neoworks-dev/otter-app/titleDetail';

	let { data } = $props();
	let m = $derived(data.title);

	let cast = $derived(m?.cast ?? []);
	let related = $derived(m?.related ?? []);
	let directors = $derived(m?.directors ?? []);

	let backdrop = $derived(backdropUrl(m?.backdropPath) ?? posterUrl(m?.posterPath));
	let runtime = $derived(formatRuntime(m?.runtime));

	let trailerKey = $derived(m?.trailerKey ?? null);
	let trailerPlaying = $state(false);

	// Scroll offset drives the banner parallax, read from the layout's scroll
	// container (the page itself doesn't scroll).
	let scrollY = $state(0);
	let bannerEl = $state<HTMLElement | null>(null);

	$effect(() => {
		if (!bannerEl) return;
		return watchScrollTop(bannerEl, (top) => (scrollY = top));
	});

	// Vertical keyboard navigation between the page sections, same coordinator
	// pattern as HomeSections. Left edge opens the sidebar.
	type SectionRef = { focus: () => void; focusFirst: () => void };
	let refs = $state<Record<string, SectionRef | undefined>>({});
	let lastSectionKey = 'sources';

	function presentKeys(): string[] {
		const keys = ['sources'];
		if (trailerKey) keys.push('trailer');
		if (cast.length > 0) keys.push('cast');
		if (related.length > 0) keys.push('related');
		return keys;
	}

	function move(fromKey: string, direction: 'up' | 'down') {
		const keys = presentKeys();
		const index = keys.indexOf(fromKey);
		let targetIndex = index - 1;
		if (direction === 'down') targetIndex = index + 1;
		const targetKey = keys[targetIndex];
		if (!targetKey) return;
		lastSectionKey = targetKey;
		refs[targetKey]?.focusFirst();
	}

	function handleEdge(fromKey: string, direction: 'left' | 'right' | 'up' | 'down') {
		if (direction === 'left') {
			sidebarFocus.openSidebar();
			return;
		}
		if (direction === 'up' || direction === 'down') {
			move(fromKey, direction);
		}
	}

	// Initial focus: the Play button, once the page has rendered.
	let initialFocusDone = false;
	$effect(() => {
		if (!m || initialFocusDone) return;
		initialFocusDone = true;
		queueMicrotask(() => refs.sources?.focusFirst());
	});

	// Focus returns to the last section when the sidebar closes.
	let sidebarWasOpen = false;
	$effect(() => {
		const open = $sidebarFocus.open;
		if (sidebarWasOpen && !open) {
			queueMicrotask(() => refs[lastSectionKey]?.focus());
		}
		sidebarWasOpen = open;
	});

	function openActor(member: CastMember) {
		goto(`/persons/${member.personId}`);
	}

	function openRelated(item: RailItem) {
		goto(detailPath(item));
	}
</script>

{#if !m}
	<div class="flex h-64 items-center justify-center">
		<span class="text-base-content/40">Movie not found</span>
	</div>
{:else}
	<!-- Large backdrop banner (a touch under full height so the next section
	     peeks below the fold) with a parallax drift on the image as you scroll. -->
	<div bind:this={bannerEl} class="relative -m-6 mb-0 h-[85svh] min-h-96 overflow-hidden">
		{#if backdrop}
			<img
				src={backdrop}
				alt=""
				class="absolute inset-0 h-full w-full object-cover will-change-transform"
				style="transform: translate3d(0, {scrollY * 0.4}px, 0) scale(1.18);"
				aria-hidden="true"
			/>
		{/if}
		<div
			class="from-base-100 absolute inset-0 bg-gradient-to-t via-transparent to-transparent"
		></div>
		<div class="from-base-100/70 absolute inset-0 bg-gradient-to-r to-transparent"></div>

		<!-- Title + meta sit near the banner's bottom so the trailer follows
		     directly beneath the description. -->
		<div class="absolute right-10 bottom-8 left-10 flex flex-col gap-4 sm:flex-row sm:items-end">
			{#if posterUrl(m.posterPath)}
				<img
					src={posterUrl(m.posterPath)}
					alt={m.title}
					class="ring-base-100/10 hidden h-72 w-48 flex-shrink-0 rounded-xl object-cover shadow-2xl ring-1 sm:block"
				/>
			{/if}

			<div class="flex flex-1 flex-col gap-3">
				<h1 class="text-6xl leading-tight font-bold">{m.title}</h1>
				{#if m.tagline}
					<p class="text-base-content/50 text-sm italic">{m.tagline}</p>
				{/if}

				<div class="text-base-content/60 flex flex-wrap items-center gap-2 text-sm">
					{#if m.year}<span>{m.year}</span>{/if}
					{#if m.year && runtime}<span class="opacity-30">·</span>{/if}
					{#if runtime}<span>{runtime}</span>{/if}
					{#if m.voteAverage > 0}
						<span class="opacity-30">·</span>
						<span>★ {m.voteAverage.toFixed(1)}</span>
					{/if}
					{#if directors.length > 0}
						<span class="opacity-30">·</span>
						<span>Regie: {directors.join(', ')}</span>
					{/if}
				</div>

				{#if m.overview}
					<p class="text-base-content/75 max-w-6xl text-base leading-relaxed">{m.overview}</p>
				{/if}

				<div class="flex flex-wrap items-end gap-6 pt-1">
					<WatchlistButton mediaId={m.id} />
					<DownloadButton mediaId={m.id} title={m.title} />
					<RatingButtons mediaId={m.id} />
				</div>

				<!-- Sources stream in live as plugins answer. -->
				<div class="pt-2">
					<StreamList
						bind:this={refs.sources}
						title={m.title}
						mediaId={m.id}
						onedge={(dir) => handleEdge('sources', dir)}
					/>
				</div>
			</div>
		</div>
	</div>

	<!-- Trailer -->
	{#if trailerKey}
		<div class="mt-12">
			<h2 class="mb-4 text-lg font-semibold">Trailer</h2>
			{#if trailerPlaying}
				<div class="aspect-video max-w-3xl overflow-hidden rounded-xl bg-black">
					<iframe
						src={`https://www.youtube-nocookie.com/embed/${trailerKey}?autoplay=1`}
						title="{m.title} trailer"
						class="h-full w-full"
						allow="autoplay; encrypted-media; fullscreen"
						allowfullscreen
					></iframe>
				</div>
			{:else}
				<FocusList
					bind:this={refs.trailer}
					items={[trailerKey]}
					orientation="horizontal"
					ariaLabel="Trailer"
					onselect={() => (trailerPlaying = true)}
					onedge={(dir) => handleEdge('trailer', dir)}
				>
					{#snippet item(youtubeKey)}
						<div
							class="group relative aspect-video w-full max-w-3xl overflow-hidden rounded-xl bg-black"
						>
							<img
								src={`https://img.youtube.com/vi/${youtubeKey}/hqdefault.jpg`}
								alt="{m.title} trailer"
								class="h-full w-full object-cover opacity-80 transition-opacity group-hover:opacity-100"
							/>
							<span
								class="absolute inset-0 m-auto flex h-16 w-16 items-center justify-center rounded-full bg-white/90 text-black shadow-xl"
							>
								<PlayIcon size={28} weight="fill" />
							</span>
						</div>
					{/snippet}
				</FocusList>
			{/if}
		</div>
	{/if}

	<!-- Cast -->
	{#if cast.length > 0}
		<div class="mt-12">
			<h2 class="mb-4 text-lg font-semibold">Cast</h2>
			<FocusList
				bind:this={refs.cast}
				items={cast}
				orientation="horizontal"
				ariaLabel="Cast"
				onselect={(member) => openActor(member)}
				onedge={(dir) => handleEdge('cast', dir)}
			>
				{#snippet item(member)}
					<div class="flex w-28 shrink-0 flex-col gap-2">
						<div class="tv-card aspect-[2/3] w-28">
							{#if member.profileUrl}
								<img
									src={member.profileUrl}
									alt={member.name}
									loading="lazy"
									class="h-full w-full object-cover"
								/>
							{:else}
								<div class="flex h-full w-full items-center justify-center">
									<span class="text-3xl opacity-20">👤</span>
								</div>
							{/if}
						</div>
						<div class="flex flex-col">
							<span class="line-clamp-1 text-sm font-medium">{member.name}</span>
							{#if member.character}
								<span class="text-base-content/50 line-clamp-1 text-xs">{member.character}</span>
							{/if}
						</div>
					</div>
				{/snippet}
			</FocusList>
		</div>
	{/if}

	<!-- Related titles -->
	{#if related.length > 0}
		<div class="mt-12">
			<MediaRail
				bind:this={refs.related}
				title="More like this"
				items={related}
				onselect={openRelated}
				onedge={(direction) => move('related', direction)}
			/>
		</div>
	{/if}

	<!-- Breathing room so focused (scaled) cards in the last rail aren't clipped. -->
	<div class="h-24"></div>
{/if}
