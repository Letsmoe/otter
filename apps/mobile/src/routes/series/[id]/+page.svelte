<script lang="ts">
	// Netflix-style show page: trailer banner, title and facts, Play or Resume of
	// the up-next episode, description and cast lines, My List / Rate / Share,
	// then Episodes (with a season switch), More Like This and Trailers.
	import { backdropUrl, posterUrl } from '@neoworks-dev/otter-app/tmdb';
	import { activeProfile } from '@neoworks-dev/otter-app/profile';
	import { loadTitleRails, type TitleRail } from '@neoworks-dev/otter-app/titleDetail';
	import {
		episodeCode,
		loadEpisodes,
		upNextEpisode,
		type SeasonEpisode
	} from '@neoworks-dev/otter-app/seriesDetail';
	import Player from '$lib/components/player/Player.svelte';
	import type { PlayerEpisode } from '$lib/components/player/EpisodesSheet.svelte';
	import TitleBackdrop from '$lib/components/title/TitleBackdrop.svelte';
	import ActionRow from '$lib/components/title/ActionRow.svelte';
	import PlayButton from '$lib/components/title/PlayButton.svelte';
	import EpisodeList from '$lib/components/title/EpisodeList.svelte';
	import TrailerTab from '$lib/components/title/TrailerTab.svelte';
	import FloatingNav from '$lib/components/title/FloatingNav.svelte';
	import SegmentedBar from '$lib/components/SegmentedBar.svelte';
	import FilterSelect from '$lib/components/FilterSelect.svelte';
	import PosterRail from '$lib/components/home/PosterRail.svelte';
	import { StreamPlayback } from '$lib/playback.svelte';
	import { together } from '$lib/stores/together.svelte';

	const STARRING_COUNT = 3;

	type Tab = 'episodes' | 'related' | 'trailers';

	const TABS: { value: Tab; label: string }[] = [
		{ value: 'episodes', label: 'Episodes' },
		{ value: 'related', label: 'More Like This' },
		{ value: 'trailers', label: 'Trailers & More' }
	];

	let { data } = $props();
	let series = $derived(data.title);

	const playback = new StreamPlayback();
	let tab = $state<Tab>('episodes');
	let overviewExpanded = $state(false);
	let rails = $state<TitleRail[]>([]);
	let railsLoaded = $state(false);

	// All episodes load once on the client (progress needs the active profile).
	let allEpisodes = $state<SeasonEpisode[]>([]);
	let episodesLoaded = $state(false);
	let shownSeason = $state<number | null>(null);
	let playingEpisode = $state<SeasonEpisode | null>(null);

	let banner = $derived(backdropUrl(series?.backdropPath) || posterUrl(series?.posterPath));
	let seasons = $derived(series?.seasons || []);
	let starring = $derived(
		(series?.cast || []).slice(0, STARRING_COUNT).map((member) => member.name)
	);
	let upNext = $derived(linkedOrUpNext(allEpisodes));
	let seasonEpisodes = $derived(
		allEpisodes.filter((episode) => episode.seasonNumber === shownSeason)
	);
	let seasonOptions = $derived(
		seasons.map((season) => ({ value: String(season.seasonNumber), label: seasonLabel(season) }))
	);
	let playerEpisodes = $derived(allEpisodes.map(toPlayerEpisode));
	let nextEpisode = $derived(episodeAfter(playingEpisode));
	let hasRails = $derived(rails.some((rail) => rail.items.length > 0));

	$effect(() => {
		const detail = series;
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

	$effect(() => {
		const profile = $activeProfile;
		const seriesId = data.id;
		if (!profile) return;
		let cancelled = false;
		episodesLoaded = false;
		loadEpisodes(seriesId, profile.id).then((loaded) => {
			if (cancelled) return;
			allEpisodes = loaded;
			episodesLoaded = true;
			if (shownSeason === null) shownSeason = initialSeason();
		});
		return () => {
			cancelled = true;
		};
	});

	$effect(() => () => playback.stop());

	// Watch Together: someone else started an episode of this show.
	$effect(() => {
		const pending = together.remoteMedia;
		if (!pending || !episodesLoaded) return;
		const episode = allEpisodes.find((candidate) => candidate.id === pending.mediaId);
		if (!episode || !together.takeRemoteMedia(episode.id)) return;
		playingEpisode = episode;
		playback.follow(episode.id);
		playback.playShared(pending.source);
	});

	// A deep link (Continue Watching) picks the episode; otherwise the profile's progress does.
	function linkedOrUpNext(episodes: SeasonEpisode[]) {
		const linked = episodes.find((episode) => episode.id === data.initialEpisodeId);
		if (linked) return { episode: linked, resume: linked.progress !== undefined };
		return upNextEpisode(episodes);
	}

	function initialSeason(): number | null {
		const linkedSeason = seasons.find((season) => season.seasonNumber === data.initialSeasonNumber);
		if (linkedSeason) return linkedSeason.seasonNumber;
		if (upNext) return upNext.episode.seasonNumber;
		if (seasons.length > 0) return seasons[0].seasonNumber;
		return null;
	}

	function seasonLabel(season: { seasonNumber: number; name: string | null }): string {
		if (season.name) return season.name;
		return `Season ${season.seasonNumber}`;
	}

	function seasonCountLabel(count: number): string {
		if (count === 1) return '1 Season';
		return `${count} Seasons`;
	}

	function regularSeasonCount(): number {
		return seasons.filter((season) => season.seasonNumber > 0).length;
	}

	function playLabel(): string {
		if (!upNext) return 'Play';
		const code = episodeCode(upNext.episode);
		if (upNext.resume) return `Resume ${code}`;
		return `Play ${code}`;
	}

	function shareUrl(): string {
		if (!series) return '';
		const tmdbId = series.id.split(':')[1];
		return `https://www.themoviedb.org/tv/${tmdbId}`;
	}

	function episodeName(episode: SeasonEpisode): string {
		if (episode.name) return episode.name;
		return `Episode ${episode.episodeNumber}`;
	}

	function playerTitle(): string {
		if (!series || !playingEpisode) return '';
		return `${series.title} · ${episodeCode(playingEpisode)} ${episodeName(playingEpisode)}`;
	}

	function toPlayerEpisode(episode: SeasonEpisode): PlayerEpisode {
		return {
			id: episode.id,
			seasonNumber: episode.seasonNumber,
			episodeNumber: episode.episodeNumber,
			name: episode.name,
			overview: episode.overview,
			stillUrl: backdropUrl(episode.stillPath, 'w780'),
			progress: episode.progress
		};
	}

	function episodeAfter(episode: SeasonEpisode | null): SeasonEpisode | null {
		if (!episode?.next) return null;
		const nextId = episode.next.id;
		const following = allEpisodes.find((candidate) => candidate.id === nextId);
		if (!following) return null;
		return following;
	}

	function playEpisode(episode: SeasonEpisode) {
		playingEpisode = episode;
		playback.follow(episode.id);
		playback.play();
	}

	function playEpisodeById(episodeId: string) {
		const episode = allEpisodes.find((candidate) => candidate.id === episodeId);
		if (episode) playEpisode(episode);
	}

	function playUpNext() {
		if (upNext) playEpisode(upNext.episode);
	}

	// Without a following episode the player hides Next Ep.
	function nextEpisodeHandler(): (() => void) | undefined {
		const following = nextEpisode;
		if (!following) return undefined;
		return () => playEpisode(following);
	}

	// Progress changed while watching; reload so Resume and the bars are current.
	async function closePlayer() {
		playback.close();
		playback.stop();
		playingEpisode = null;
		const profile = $activeProfile;
		if (!profile) return;
		allEpisodes = await loadEpisodes(data.id, profile.id);
	}
</script>

{#if playback.playerOpen && playingEpisode}
	<Player
		src={playback.playing?.src || ''}
		mimeType={playback.playing?.mimeType || ''}
		title={playerTitle()}
		mediaId={playingEpisode.id}
		status={playback.status}
		loadError={playback.error}
		sources={playback.sourceOptions}
		currentSourceId={playback.playing?.sourceId}
		sharedSource={playback.playingSource}
		onsource={(sourceId) => playback.switchSource(sourceId)}
		onnext={nextEpisodeHandler()}
		episodes={playerEpisodes}
		onepisode={playEpisodeById}
		onclose={closePlayer}
	/>
{/if}

<FloatingNav />

{#if !series}
	<div class="flex h-64 items-center justify-center text-white/40">Show not found</div>
{:else}
	<div class="-mt-[var(--safe-top)] flex flex-col gap-3">
		<TitleBackdrop imageUrl={banner} trailerKey={series.trailerKey} title={series.title} />

		<h1 class="text-2xl leading-tight font-bold">{series.title}</h1>

		<div class="flex flex-wrap items-center gap-x-3 gap-y-1 text-sm text-white/60">
			{#if series.year}<span>{series.year}</span>{/if}
			{#if regularSeasonCount() > 0}<span>{seasonCountLabel(regularSeasonCount())}</span>{/if}
			{#if series.voteAverage > 0}<span>★ {series.voteAverage.toFixed(1)}</span>{/if}
			{#if series.genres.length > 0}<span>{series.genres.slice(0, 2).join(' · ')}</span>{/if}
		</div>

		<PlayButton
			label={playLabel()}
			busy={!episodesLoaded}
			ready={episodesLoaded && upNext !== null}
			disabled={episodesLoaded && upNext === null}
			onclick={playUpNext}
		/>

		{#if upNext?.resume}
			<div class="flex items-center gap-3 text-xs text-white/60">
				<span class="h-1 flex-1 overflow-hidden rounded-full bg-white/15">
					<span
						class="bg-primary block h-full"
						style:width="{(upNext.episode.progress || 0) * 100}%"
					></span>
				</span>
				<span class="shrink-0">{episodeCode(upNext.episode)} · {episodeName(upNext.episode)}</span>
			</div>
		{/if}

		{#if series.overview}
			<button
				class="text-left text-sm leading-relaxed text-white/85"
				class:line-clamp-4={!overviewExpanded}
				onclick={() => (overviewExpanded = !overviewExpanded)}
			>
				{series.overview}
			</button>
		{/if}

		{#if starring.length > 0}
			<p class="line-clamp-1 text-xs text-white/50">Starring: {starring.join(', ')}</p>
		{/if}

		<ActionRow mediaId={series.id} title={series.title} shareUrl={shareUrl()} />

		<div class="mt-3">
			<SegmentedBar options={TABS} selected={tab} onselect={(chosen) => (tab = chosen)} />
		</div>

		<div class="pt-2 pb-4">
			{#if tab === 'episodes'}
				<div class="flex flex-col gap-4">
					{#if seasonOptions.length > 1 && shownSeason !== null}
						<div>
							<FilterSelect
								label="Season"
								showLabel={false}
								options={seasonOptions}
								selected={String(shownSeason)}
								onselect={(value) => (shownSeason = Number(value))}
							/>
						</div>
					{/if}
					{#if !episodesLoaded && allEpisodes.length === 0}
						<span class="loading loading-spinner mx-auto my-6"></span>
					{:else if seasonEpisodes.length === 0}
						<p class="py-6 text-center text-sm text-white/50">No episodes yet.</p>
					{:else}
						<EpisodeList episodes={seasonEpisodes} onplay={playEpisode} />
					{/if}
				</div>
			{:else if tab === 'related'}
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
			{:else}
				<TrailerTab trailerKey={series.trailerKey} title={series.title} />
			{/if}
		</div>
	</div>
{/if}
