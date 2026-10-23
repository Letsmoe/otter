<script lang="ts">
	import { goto } from '$app/navigation';
	import PlayIcon from 'phosphor-svelte/lib/PlayIcon';
	import SkipForwardIcon from 'phosphor-svelte/lib/SkipForwardIcon';
	import StreamList from '$lib/components/StreamList.svelte';
	import FocusList from '$lib/components/FocusList.svelte';
	import MediaRail from '$lib/components/MediaRail.svelte';
	import WatchlistButton from '$lib/components/WatchlistButton.svelte';
	import DownloadButton from '$lib/components/downloads/DownloadButton.svelte';
	import { activeProfile } from '@neoworks-dev/otter-app/profile';
	import { sidebarFocus } from '$lib/stores/focus';
	import { watchScrollTop } from '$lib/scroll';
	import { posterUrl, backdropUrl, formatRuntime } from '@neoworks-dev/otter-app/tmdb';
	import { detailPath, episodeLabel, type EpisodeRef, type RailItem } from '@neoworks-dev/otter-app/discovery';
	import { loadEpisodes, type SeasonEpisode } from '@neoworks-dev/otter-app/seriesDetail';

	let { data } = $props();
	let s = $derived(data.title);
	let seasons = $derived(s?.seasons ?? []);
	let cast = $derived(s?.cast ?? []);
	let related = $derived(s?.related ?? []);
	let trailerKey = $derived(s?.trailerKey ?? null);

	let backdrop = $derived(backdropUrl(s?.backdropPath) ?? posterUrl(s?.posterPath));

	// All episodes load once on the client (progress needs the active profile).
	let allEpisodes = $state<SeasonEpisode[]>([]);
	let episodesLoading = $state(true);

	let activeSeasonNumber = $state<number | null>(null);
	let activeEpisodeId = $state<string | null>(null);

	let episodes = $derived(
		allEpisodes.filter((episode) => episode.seasonNumber === activeSeasonNumber)
	);
	let activeEpisode = $derived(allEpisodes.find((episode) => episode.id === activeEpisodeId));

	let trailerPlaying = $state(false);

	// Scroll offset drives the banner parallax, read from the layout's scroll
	// container (the page itself doesn't scroll).
	let scrollY = $state(0);
	let bannerEl = $state<HTMLElement | null>(null);

	$effect(() => {
		if (!bannerEl) return;
		return watchScrollTop(bannerEl, (top) => (scrollY = top));
	});

	function stillUrl(path: string | null): string | null {
		return backdropUrl(path, 'w780');
	}

	function seasonLabel(season: { seasonNumber: number; name: string | null }): string {
		if (season.name) return season.name;
		return `Season ${season.seasonNumber}`;
	}

	function seasonCountLabel(count: number): string {
		if (count === 1) return '1 season';
		return `${count} seasons`;
	}

	// Specials (season 0) are only the default when nothing else exists.
	function defaultSeasonNumber(): number | null {
		if (seasons.length === 0) return null;
		const regular = seasons.find((season) => season.seasonNumber > 0);
		if (regular) return regular.seasonNumber;
		return seasons[0].seasonNumber;
	}

	function firstEpisodeId(seasonNumber: number): string | null {
		const first = allEpisodes.find((episode) => episode.seasonNumber === seasonNumber);
		if (!first) return null;
		return first.id;
	}

	function selectSeason(seasonNumber: number) {
		activeSeasonNumber = seasonNumber;
		activeEpisodeId = firstEpisodeId(seasonNumber);
	}

	function selectEpisode(episode: EpisodeRef) {
		activeSeasonNumber = episode.seasonNumber;
		activeEpisodeId = episode.id;
	}

	// Initial selection: the deep-linked episode/season if valid, else the default season.
	function applyInitialSelection() {
		const linkedEpisode = allEpisodes.find((episode) => episode.id === data.initialEpisodeId);
		if (linkedEpisode) {
			selectEpisode(linkedEpisode);
			return;
		}
		const linkedSeason = seasons.find((season) => season.seasonNumber === data.initialSeasonNumber);
		if (linkedSeason) {
			selectSeason(linkedSeason.seasonNumber);
			return;
		}
		const fallback = defaultSeasonNumber();
		if (fallback !== null) selectSeason(fallback);
	}

	$effect(() => {
		const profile = $activeProfile;
		const seriesId = data.id;
		if (!profile) return;
		let cancelled = false;
		episodesLoading = true;
		loadEpisodes(seriesId, profile.id).then((loaded) => {
			if (cancelled) return;
			allEpisodes = loaded;
			episodesLoading = false;
			applyInitialSelection();
		});
		return () => {
			cancelled = true;
		};
	});

	// Vertical keyboard navigation between the page sections, same coordinator
	// pattern as the movie detail page. Left edge opens the sidebar.
	type SectionRef = { focus: () => void; focusFirst: () => void };
	let refs = $state<Record<string, SectionRef | undefined>>({});
	let lastSectionKey = 'seasons';

	function presentKeys(): string[] {
		const keys: string[] = [];
		if (seasons.length > 0) keys.push('seasons');
		if (episodes.length > 0) keys.push('episodes');
		if (activeEpisode) keys.push('sources');
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

	// Initial focus lands on the season selector once seasons render.
	let initialFocusDone = false;
	$effect(() => {
		if (!s || initialFocusDone) return;
		const first = presentKeys()[0];
		if (!first) return;
		initialFocusDone = true;
		queueMicrotask(() => refs[first]?.focusFirst());
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

	function openActor(personId: string) {
		goto(`/persons/${personId}`);
	}

	function openRelated(item: RailItem) {
		goto(detailPath(item));
	}

	function episodeStreamTitle(seriesTitle: string, episode: SeasonEpisode): string {
		return `${seriesTitle} S${episode.seasonNumber}E${episode.episodeNumber}`;
	}
</script>

{#if !s}
	<div class="flex h-64 items-center justify-center">
		<span class="text-base-content/40">Series not found</span>
	</div>
{:else}
	<!-- Backdrop banner with parallax drift, matching the movie detail page. -->
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
		<div class="from-base-100 absolute inset-0 bg-gradient-to-t via-transparent to-transparent"></div>
		<div class="from-base-100/70 absolute inset-0 bg-gradient-to-r to-transparent"></div>

		<div class="absolute right-10 bottom-8 left-10 flex flex-col gap-4 sm:flex-row sm:items-end">
			{#if posterUrl(s.posterPath)}
				<img
					src={posterUrl(s.posterPath)}
					alt={s.title}
					class="ring-base-100/10 hidden h-72 w-48 flex-shrink-0 rounded-xl object-cover shadow-2xl ring-1 sm:block"
				/>
			{/if}

			<div class="flex flex-1 flex-col gap-3">
				<h1 class="text-6xl font-bold leading-tight">{s.title}</h1>
				{#if s.tagline}
					<p class="text-base-content/50 text-sm italic">{s.tagline}</p>
				{/if}

				<div class="text-base-content/60 flex flex-wrap items-center gap-2 text-sm">
					{#if s.year}<span>{s.year}</span>{/if}
					{#if s.status}<span class="opacity-30">·</span><span class="capitalize">{s.status}</span>{/if}
					{#if seasons.length > 0}
						<span class="opacity-30">·</span>
						<span>{seasonCountLabel(seasons.length)}</span>
					{/if}
					{#if s.voteAverage > 0}
						<span class="opacity-30">·</span>
						<span>★ {s.voteAverage.toFixed(1)}</span>
					{/if}
				</div>

				{#if s.overview}
					<p class="text-base-content/75 max-w-2xl text-sm leading-relaxed">{s.overview}</p>
				{/if}

				<div class="flex flex-wrap items-center gap-2 pt-1">
					<WatchlistButton mediaId={s.id} />
				</div>
			</div>
		</div>
	</div>

	<!-- Season selector -->
	{#if seasons.length > 0}
		<div class="mt-10">
			<h2 class="mb-4 text-lg font-semibold">Seasons</h2>
			<FocusList
				bind:this={refs.seasons}
				items={seasons}
				orientation="horizontal"
				ariaLabel="Seasons"
				onselect={(season) => selectSeason(season.seasonNumber)}
				onedge={(dir) => handleEdge('seasons', dir)}
			>
				{#snippet item(season)}
					<div
						class="flex h-11 items-center rounded-lg px-5 text-sm font-semibold whitespace-nowrap {activeSeasonNumber ===
						season.seasonNumber
							? 'bg-white text-black'
							: 'bg-white/10 text-white'}"
					>
						{seasonLabel(season)}
					</div>
				{/snippet}
			</FocusList>
		</div>
	{/if}

	{#if episodesLoading && episodes.length === 0}
		<div class="mt-10 flex justify-center py-8">
			<span class="text-base-content/40 text-sm">Loading season…</span>
		</div>
	{/if}

	<!-- Episodes -->
	{#if episodes.length > 0}
		<div class="mt-12">
			<h2 class="mb-4 text-lg font-semibold">Episodes</h2>
			<FocusList
				bind:this={refs.episodes}
				items={episodes}
				orientation="horizontal"
				ariaLabel="Episodes"
				onselect={(episode) => {
					selectEpisode(episode);
					queueMicrotask(() => refs.sources?.focusFirst());
				}}
				onedge={(dir) => handleEdge('episodes', dir)}
			>
				{#snippet item(episode)}
					<div class="flex w-72 shrink-0 flex-col gap-2">
						<div
							class="tv-card aspect-video w-72 {activeEpisodeId === episode.id
								? 'ring-primary ring-2'
								: ''}"
						>
							{#if stillUrl(episode.stillPath)}
								<img
									src={stillUrl(episode.stillPath)}
									alt={episode.name ?? `Episode ${episode.episodeNumber}`}
									loading="lazy"
									class="h-full w-full object-cover"
								/>
							{:else}
								<div class="flex h-full w-full items-center justify-center">
									<span class="text-3xl opacity-20">🎬</span>
								</div>
							{/if}
							{#if episode.progress !== undefined}
								<div class="absolute inset-x-0 bottom-0 h-1 bg-black/50">
									<div class="bg-primary h-full" style="width: {episode.progress * 100}%"></div>
								</div>
							{/if}
						</div>
						<div class="flex flex-col">
							<span class="text-base-content/50 text-xs tabular-nums">Episode {episode.episodeNumber}</span>
							<span class="line-clamp-1 text-sm font-medium">
								{episode.name ?? `Episode ${episode.episodeNumber}`}
							</span>
						</div>
					</div>
				{/snippet}
			</FocusList>
		</div>
	{/if}

	<!-- Sources for the selected episode -->
	{#if activeEpisode}
		<div class="mt-12">
			<h2 class="mb-1 text-lg font-semibold">
				<PlayIcon size={18} weight="fill" class="mb-1 inline" />
				Episode {activeEpisode.episodeNumber}
				{#if activeEpisode.name}· {activeEpisode.name}{/if}
			</h2>
			<div class="text-base-content/60 mb-4 flex flex-wrap items-center gap-2 text-sm">
				{#if activeEpisode.airDate}<span>{activeEpisode.airDate}</span>{/if}
				{#if activeEpisode.airDate && formatRuntime(activeEpisode.runtime)}<span class="opacity-30">·</span>{/if}
				{#if formatRuntime(activeEpisode.runtime)}<span>{formatRuntime(activeEpisode.runtime)}</span>{/if}
			</div>
			{#if activeEpisode.overview}
				<p class="text-base-content/75 mb-4 max-w-2xl text-sm leading-relaxed">{activeEpisode.overview}</p>
			{/if}

			<StreamList
				bind:this={refs.sources}
				title={episodeStreamTitle(s.title, activeEpisode)}
				mediaId={activeEpisode.id}
				onedge={(dir) => handleEdge('sources', dir)}
			/>

			<div class="mt-4 flex flex-wrap items-center gap-3">
				<DownloadButton
					mediaId={activeEpisode.id}
					title={episodeStreamTitle(s.title, activeEpisode)}
				/>
				{#if activeEpisode.next}
					{@const nextEpisode = activeEpisode.next}
					<button
						type="button"
						onclick={() => selectEpisode(nextEpisode)}
						class="flex items-center gap-2 rounded-lg bg-white/10 px-4 py-2.5 text-sm font-semibold text-white transition-colors hover:bg-white/20"
					>
						<SkipForwardIcon size={16} weight="fill" />
						Next: {episodeLabel(nextEpisode)}
					</button>
				{/if}
			</div>
		</div>
	{/if}

	<!-- Trailer -->
	{#if trailerKey}
		<div class="mt-12">
			<h2 class="mb-4 text-lg font-semibold">Trailer</h2>
			{#if trailerPlaying}
				<div class="aspect-video max-w-3xl overflow-hidden rounded-xl bg-black">
					<iframe
						src={`https://www.youtube-nocookie.com/embed/${trailerKey}?autoplay=1`}
						title="{s.title} trailer"
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
						<div class="group relative aspect-video w-full max-w-3xl overflow-hidden rounded-xl bg-black">
							<img
								src={`https://img.youtube.com/vi/${youtubeKey}/hqdefault.jpg`}
								alt="{s.title} trailer"
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
				onselect={(member) => openActor(member.personId)}
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
