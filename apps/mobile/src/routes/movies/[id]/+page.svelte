<script lang="ts">
	// Netflix-style movie page: trailer banner, title and facts, Play, description,
	// cast lines, My List / Rate / Share, then More Like This, Trailers and Sources.
	import { backdropUrl, formatRuntime, posterUrl } from '@neoworks-dev/otter-app/tmdb';
	import { loadTitleRails, type TitleRail } from '@neoworks-dev/otter-app/titleDetail';
	import Player from '$lib/components/player/Player.svelte';
	import TitleBackdrop from '$lib/components/title/TitleBackdrop.svelte';
	import ActionRow from '$lib/components/title/ActionRow.svelte';
	import SourceList from '$lib/components/title/SourceList.svelte';
	import PlayButton from '$lib/components/title/PlayButton.svelte';
	import TrailerTab from '$lib/components/title/TrailerTab.svelte';
	import SegmentedBar from '$lib/components/SegmentedBar.svelte';
	import FloatingNav from '$lib/components/title/FloatingNav.svelte';
	import PosterRail from '$lib/components/home/PosterRail.svelte';
	import { notifications } from '$lib/stores/notifications';
	import { StreamPlayback } from '$lib/playback.svelte';
	import { together } from '$lib/stores/together.svelte';

	const STARRING_COUNT = 3;

	type Tab = 'related' | 'trailers' | 'sources';

	const TABS: { value: Tab; label: string }[] = [
		{ value: 'related', label: 'More Like This' },
		{ value: 'trailers', label: 'Trailers & More' },
		{ value: 'sources', label: 'Sources' }
	];

	let { data } = $props();
	let movie = $derived(data.title);

	const playback = new StreamPlayback();
	let tab = $state<Tab>('related');
	let overviewExpanded = $state(false);
	let rails = $state<TitleRail[]>([]);
	let railsLoaded = $state(false);

	let banner = $derived(backdropUrl(movie?.backdropPath) || posterUrl(movie?.posterPath));
	let runtime = $derived(formatRuntime(movie?.runtime));
	let starring = $derived(
		(movie?.cast || []).slice(0, STARRING_COUNT).map((member) => member.name)
	);

	$effect(() => {
		const detail = movie;
		rails = [];
		railsLoaded = false;
		if (!detail) return;
		let cancelled = false;
		loadTitleRails(detail).then((loaded) => {
			if (cancelled) return;
			rails = loaded;
			railsLoaded = true;
		});
		return () => {
			cancelled = true;
		};
	});

	let hasRails = $derived(rails.some((rail) => rail.items.length > 0));

	$effect(() => {
		if (!movie) return;
		playback.follow(movie.id);
		return () => playback.stop();
	});

	// Watch Together: someone else started this movie.
	$effect(() => {
		if (!movie || !together.remoteMedia) return;
		const started = together.takeRemoteMedia(movie.id);
		if (!started) return;
		playback.playShared(started.source);
	});

	function shareUrl(): string {
		if (!movie) return '';
		const tmdbId = movie.id.split(':')[1];
		return `https://www.themoviedb.org/movie/${tmdbId}`;
	}

	function playLabel(): string {
		if (playback.nothingFound) return 'No sources yet';
		return 'Play';
	}

	async function searchAgain() {
		if (await playback.searchAgain()) return;
		notifications.push('Could not start a source lookup', 'error');
	}
</script>

{#if playback.playerOpen && movie}
	<Player
		src={playback.playing?.src || ''}
		mimeType={playback.playing?.mimeType || ''}
		title={movie.title}
		mediaId={movie.id}
		status={playback.status}
		loadError={playback.error}
		sources={playback.sourceOptions}
		currentSourceId={playback.playing?.sourceId}
		sharedSource={playback.playingSource}
		onsource={(sourceId) => playback.switchSource(sourceId)}
		onclose={() => playback.close()}
	/>
{/if}

<FloatingNav />

{#if !movie}
	<div class="flex h-64 items-center justify-center text-white/40">Movie not found</div>
{:else}
	<div class="-mt-[var(--safe-top)] flex flex-col gap-3">
		<TitleBackdrop imageUrl={banner} trailerKey={movie.trailerKey} title={movie.title} />

		<h1 class="text-2xl leading-tight font-bold">{movie.title}</h1>

		<div class="flex flex-wrap items-center gap-x-3 gap-y-1 text-sm text-white/60">
			{#if movie.year}<span>{movie.year}</span>{/if}
			{#if runtime}<span>{runtime}</span>{/if}
			{#if movie.voteAverage > 0}<span>★ {movie.voteAverage.toFixed(1)}</span>{/if}
			{#if movie.genres.length > 0}<span>{movie.genres.slice(0, 2).join(' · ')}</span>{/if}
		</div>

		<PlayButton
			label={playLabel()}
			busy={playback.busy}
			ready={!playback.busy && playback.streams.length > 0}
			disabled={playback.nothingFound}
			onclick={() => playback.play()}
		/>

		{#if movie.overview}
			<button
				class="text-left text-sm leading-relaxed text-white/85"
				class:line-clamp-4={!overviewExpanded}
				onclick={() => (overviewExpanded = !overviewExpanded)}
			>
				{movie.overview}
			</button>
		{/if}

		<div class="flex flex-col gap-0.5 text-xs text-white/50">
			{#if starring.length > 0}
				<p class="line-clamp-1">Starring: {starring.join(', ')}</p>
			{/if}
			{#if movie.directors.length > 0}
				<p class="line-clamp-1">Director: {movie.directors.join(', ')}</p>
			{/if}
		</div>

		<ActionRow
			mediaId={movie.id}
			title={movie.title}
			shareUrl={shareUrl()}
			downloadMediaId={movie.id}
		/>

		<div class="mt-3">
			<SegmentedBar options={TABS} selected={tab} onselect={(chosen) => (tab = chosen)} />
		</div>

		<div class="pt-2 pb-4">
			{#if tab === 'related'}
				<div class="flex flex-col gap-6">
					{#each rails as rail (rail.key)}
						<PosterRail title={rail.title} items={rail.items} />
					{/each}
					{#if railsLoaded && !hasRails}
						<p class="py-6 text-center text-sm text-white/50">Nothing related yet.</p>
					{:else if !railsLoaded}
						<span class="loading loading-spinner mx-auto my-6"></span>
					{/if}
				</div>
			{:else if tab === 'trailers'}
				<TrailerTab trailerKey={movie.trailerKey} title={movie.title} />
			{:else}
				<SourceList
					streams={playback.streams}
					searching={playback.searching}
					loadingId={playback.loadingId}
					onplay={(stream) => playback.playSource(stream)}
					onsearchagain={searchAgain}
				/>
			{/if}
		</div>
	</div>
{/if}
