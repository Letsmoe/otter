<script lang="ts">
	import type { Snippet } from 'svelte';
	import { goto } from '$app/navigation';
	import FocusList from './FocusList.svelte';
	import VideoPlayer from './VideoPlayer.svelte';
	import PlayIcon from 'phosphor-svelte/lib/PlayIcon';
	import InfoIcon from 'phosphor-svelte/lib/InfoIcon';
	import BookmarkSimpleIcon from 'phosphor-svelte/lib/BookmarkSimpleIcon';
	import CheckIcon from 'phosphor-svelte/lib/CheckIcon';
	import SpeakerSimpleHighIcon from 'phosphor-svelte/lib/SpeakerSimpleHighIcon';
	import SpeakerSimpleSlashIcon from 'phosphor-svelte/lib/SpeakerSimpleSlashIcon';
	import { sidebarFocus } from '$lib/stores/focus';
	import { activeProfile } from '@neoworks-dev/otter-app/profile';
	import { notifications } from '$lib/stores/notifications';
	import { watchScrollTop } from '$lib/scroll';
	import {
		addToWatchlist,
		removeFromWatchlist,
		fetchInWatchlist,
		fetchSeasons,
		fetchTrailerKey,
		type RailItem,
		type SeasonSummary
	} from '@neoworks-dev/otter-app/discovery';
	import { fetchStoredStreams, resolveStream } from '@neoworks-dev/otter-app/streams';

	interface Props {
		item: RailItem | null;
		// Fallback navigation when the item has no stored stream yet; the detail
		// page starts a lookup.
		onplay: (item: RailItem) => void;
		oninfo: (item: RailItem) => void;
		onedge?: (direction: 'down') => void;
		// Rendered at the bottom of the banner, overlaid on the backdrop (e.g. the
		// Continue-watching rail) so the hero reads like the movie detail page.
		children?: Snippet;
	}

	let { item, onplay, oninfo, onedge, children }: Props = $props();

	type ActionKey = 'play' | 'watchlist' | 'info';
	type Action = { key: ActionKey; label: string };

	let inWatchlist = $state(false);
	let watchlistBusy = $state(false);
	let playLoading = $state(false);
	let activeStream = $state<{ src: string; mimeType: string; title: string } | null>(null);

	let isSeries = $derived(item?.mediaType === 'series');

	// Series carry a season selector; the active season drives the Play/info
	// targets and the hero subtitle.
	let seasons = $state<SeasonSummary[]>([]);
	let activeSeasonIndex = $state(0);
	let activeSeason = $derived<SeasonSummary | undefined>(seasons[activeSeasonIndex]);

	function playLabel(): string {
		if (playLoading) return 'Loading…';
		return 'Play';
	}

	function watchlistLabel(): string {
		if (inWatchlist) return 'In watchlist';
		return 'Add to watchlist';
	}

	function infoLabel(): string {
		if (isSeries) return 'View series';
		return 'More info';
	}

	let actions = $derived<Action[]>([
		{ key: 'play', label: playLabel() },
		{ key: 'watchlist', label: watchlistLabel() },
		{ key: 'info', label: infoLabel() }
	]);

	let list = $state<{ focus: () => void; focusFirst: () => void }>();
	let seasonList = $state<{ focus: () => void; focusFirst: () => void }>();

	// Scroll offset drives the banner parallax, read from the layout's scroll
	// container (the page itself doesn't scroll).
	let scrollY = $state(0);
	let sectionEl = $state<HTMLElement | null>(null);
	let sectionWidth = $state(0);
	let sectionHeight = $state(0);

	$effect(() => {
		if (!sectionEl) return;
		return watchScrollTop(sectionEl, (top) => (scrollY = top));
	});

	// Muted YouTube trailer autoplays behind the hero when the title has one.
	let trailerKey = $state<string | null>(null);
	let muted = $state(true);
	let trailerFrame = $state<HTMLIFrameElement | null>(null);

	// Scrolled to the top → show the whole trailer (contain); once scrolled away
	// it fills the banner (cover).
	let atTop = $derived(scrollY <= 8);

	const TRAILER_ASPECT = 16 / 9;

	// An iframe can't object-fit, so size it explicitly around the banner centre.
	function trailerFrameStyle(width: number, height: number, cover: boolean): string {
		let frameWidth = Math.min(width, height * TRAILER_ASPECT);
		if (cover) frameWidth = Math.max(width, height * TRAILER_ASPECT);
		const frameHeight = frameWidth / TRAILER_ASPECT;
		return `width: ${frameWidth}px; height: ${frameHeight}px;`;
	}

	function trailerEmbedUrl(key: string): string {
		const params = `autoplay=1&mute=1&loop=1&playlist=${key}&controls=0&modestbranding=1&rel=0&playsinline=1&enablejsapi=1`;
		return `https://www.youtube-nocookie.com/embed/${key}?${params}`;
	}

	$effect(() => {
		const media = item;
		trailerKey = null;
		muted = true;
		if (!media) return;
		let cancelled = false;
		fetchTrailerKey(media.id).then((key) => {
			if (!cancelled) trailerKey = key;
		});
		return () => {
			cancelled = true;
		};
	});

	// The embed is controlled through the YouTube iframe API's postMessage commands.
	function toggleMute() {
		muted = !muted;
		let command = 'unMute';
		if (muted) command = 'mute';
		const message = JSON.stringify({ event: 'command', func: command, args: [] });
		trailerFrame?.contentWindow?.postMessage(message, '*');
	}

	// Reflect current watchlist membership for the hero item.
	$effect(() => {
		const profile = $activeProfile;
		const media = item;
		if (!profile || !media) {
			inWatchlist = false;
			return;
		}
		let cancelled = false;
		fetchInWatchlist(profile.id, media.id).then((result) => {
			if (!cancelled) inWatchlist = result;
		});
		return () => {
			cancelled = true;
		};
	});

	// Load the season list for a series hero so the selector can drive Play/info.
	$effect(() => {
		const media = item;
		if (!media || media.mediaType !== 'series') {
			seasons = [];
			activeSeasonIndex = 0;
			return;
		}
		let cancelled = false;
		fetchSeasons(media.id).then((loaded) => {
			if (cancelled) return;
			seasons = loaded;
			activeSeasonIndex = 0;
		});
		return () => {
			cancelled = true;
		};
	});

	function openSeriesDetail() {
		if (!item) return;
		if (!activeSeason) {
			goto(`/series/${item.id}`);
			return;
		}
		goto(`/series/${item.id}?season=${activeSeason.seasonNumber}`);
	}

	export function focus() {
		list?.focus();
	}

	/** Focus the Play button — the default entry point. */
	export function focusFirst() {
		list?.focusFirst();
	}

	async function play() {
		if (!item || playLoading) return;
		playLoading = true;
		try {
			const streams = await fetchStoredStreams(item.id);
			const best = streams[0];
			if (!best) {
				// No stored stream yet — the detail page starts a lookup.
				onplay(item);
				return;
			}
			const resolved = await resolveStream(best.id);
			activeStream = { src: resolved.url, mimeType: resolved.mimeType, title: item.title };
		} catch {
			notifications.push('Could not play this title', 'error');
		} finally {
			playLoading = false;
		}
	}

	async function toggleWatchlist() {
		const profile = $activeProfile;
		if (!profile || !item || watchlistBusy) return;
		watchlistBusy = true;
		const next = !inWatchlist;
		inWatchlist = next;
		try {
			if (next) {
				await addToWatchlist(profile.id, item.id);
				notifications.push('Added to watchlist', 'success');
			} else {
				await removeFromWatchlist(profile.id, item.id);
				notifications.push('Removed from watchlist', 'success');
			}
		} catch {
			inWatchlist = !next;
			notifications.push('Could not update watchlist', 'error');
		} finally {
			watchlistBusy = false;
		}
	}

	function runAction(action: Action) {
		if (!item) return;
		if (action.key === 'watchlist') {
			toggleWatchlist();
			return;
		}
		if (isSeries) {
			openSeriesDetail();
			return;
		}
		if (action.key === 'play') {
			play();
			return;
		}
		oninfo(item);
	}

	function selectSeason(_season: SeasonSummary, index: number) {
		activeSeasonIndex = index;
	}

	// Actions row: left opens the sidebar; down drops to the season selector (for a
	// series) or to the next page section.
	function handleActionsEdge(direction: 'left' | 'right' | 'up' | 'down') {
		if (direction === 'left') {
			sidebarFocus.openSidebar();
			return;
		}
		if (direction !== 'down') return;
		if (isSeries && seasons.length > 0) seasonList?.focusFirst();
		else onedge?.('down');
	}

	// Season selector: up returns to the actions row, down leaves to the next section.
	function handleSeasonsEdge(direction: 'left' | 'right' | 'up' | 'down') {
		if (direction === 'left') {
			sidebarFocus.openSidebar();
		} else if (direction === 'up') {
			list?.focus();
		} else if (direction === 'down') {
			onedge?.('down');
		}
	}
</script>

{#if activeStream}
	<VideoPlayer
		src={activeStream.src}
		mimeType={activeStream.mimeType}
		title={activeStream.title}
		mediaId={item?.id}
		onclose={() => (activeStream = null)}
	/>
{/if}

{#if item}
	<!-- Full-bleed banner (bleeds past the main's p-6) with a parallax drift on the
	     backdrop. min-height + justify-end so the banner grows to contain its
	     content instead of overflowing it. -->
	<section
		bind:this={sectionEl}
		bind:clientWidth={sectionWidth}
		bind:clientHeight={sectionHeight}
		class="bg-base-100 relative -mx-6 -mt-6 mb-0 flex min-h-[85svh] flex-col justify-end overflow-hidden"
	>
		{#if item.backdropUrl}
			<!-- 20% vertical overhang; drift factor (0.18) stays under it so the image
			     never drifts past its own edge and exposes the background. -->
			<img
				src={item.backdropUrl}
				alt={item.title}
				class="absolute inset-x-0 top-[-20%] h-[140%] w-full object-cover will-change-transform"
				style="transform: translate3d(0, {scrollY * 0.18}px, 0);"
			/>
		{/if}
		{#if trailerKey}
			<!-- Muted YouTube embed; pointer events off so the banner stays clickable. -->
			<div class="pointer-events-none absolute inset-0 flex items-center justify-center">
				<iframe
					bind:this={trailerFrame}
					src={trailerEmbedUrl(trailerKey)}
					title="{item.title} trailer"
					allow="autoplay; encrypted-media"
					class="shrink-0 border-0"
					style={trailerFrameStyle(sectionWidth, sectionHeight, !atTop)}
				></iframe>
			</div>
		{/if}
		<div class="from-base-100 absolute inset-0 bg-gradient-to-t via-transparent to-transparent"></div>
		<div class="from-base-100/70 absolute inset-0 bg-gradient-to-r to-transparent"></div>

		{#if trailerKey}
			<button
				type="button"
				onclick={toggleMute}
				aria-label={muted ? 'Unmute trailer' : 'Mute trailer'}
				title={muted ? 'Unmute' : 'Mute'}
				class="absolute top-6 right-6 z-10 flex h-11 w-11 items-center justify-center rounded-full border border-white/20 bg-black/40 text-white backdrop-blur transition-colors hover:bg-black/60"
			>
				{#if muted}
					<SpeakerSimpleSlashIcon size={20} />
				{:else}
					<SpeakerSimpleHighIcon size={20} />
				{/if}
			</button>
		{/if}

		<!-- Content sits in normal flow so the banner grows to contain it (min-h +
		     justify-end pins it to the bottom when the content is short). -->
		<div class="relative flex flex-col gap-6 p-10">
			<div class="flex max-w-3xl flex-col gap-4">
				<h1 class="text-base-content text-6xl font-bold">{item.title}</h1>
				{#if isSeries && activeSeason}
					<p class="text-base-content/60 text-sm font-semibold tracking-wide uppercase">
						{activeSeason.name ?? `Season ${activeSeason.seasonNumber}`}
					</p>
				{/if}
				{#if item.overview}
					<p class="text-base-content/70 line-clamp-3 text-base leading-relaxed">{item.overview}</p>
				{/if}

				<FocusList
					bind:this={list}
					items={actions}
					orientation="horizontal"
					ariaLabel="Hero actions"
					class="flex-wrap"
					scroll="top"
					onselect={(action) => runAction(action)}
					onedge={handleActionsEdge}
				>
					{#snippet item(action)}
						<div
							class="relative flex items-center gap-2 overflow-hidden rounded-lg px-5 py-2.5 text-sm font-semibold {action.key ===
							'play'
								? 'bg-white text-black'
								: 'bg-white/10 text-white'}"
						>
							{#if action.key === 'play'}
								<PlayIcon size={16} weight="fill" />
							{:else if action.key === 'watchlist'}
								{#if inWatchlist}
									<CheckIcon size={16} weight="bold" />
								{:else}
									<BookmarkSimpleIcon size={16} />
								{/if}
							{:else}
								<InfoIcon size={16} />
							{/if}
							{action.label}
						</div>
					{/snippet}
				</FocusList>

				{#if isSeries && seasons.length > 0}
					<FocusList
						bind:this={seasonList}
						items={seasons}
						orientation="horizontal"
						ariaLabel="Seasons"
						scroll="top"
						onselect={selectSeason}
						onedge={handleSeasonsEdge}
					>
						{#snippet item(season, index)}
							<div
								class="flex h-9 items-center rounded-lg px-4 text-xs font-semibold whitespace-nowrap {index ===
								activeSeasonIndex
									? 'bg-white text-black'
									: 'bg-white/10 text-white'}"
							>
								{season.name ?? `Season ${season.seasonNumber}`}
							</div>
						{/snippet}
					</FocusList>
				{/if}
			</div>

			{@render children?.()}
		</div>
	</section>
{/if}
