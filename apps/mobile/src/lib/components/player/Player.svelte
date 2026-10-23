<script lang="ts">
	// Netflix-style fullscreen player for the phone: landscape, tap to show or
	// hide controls, double-tap a side to skip, ±10s and play/pause in the
	// middle, a scrubber with time (and frame) preview, side sheets for speed,
	// sources and audio/subtitles, a lock, resume and an end screen.
	import { onMount, untrack } from 'svelte';
	import { fade, scale } from 'svelte/transition';
	import { backOut } from 'svelte/easing';
	import { get } from 'svelte/store';
	import { App } from '@capacitor/app';
	import { ScreenOrientation } from '@capacitor/screen-orientation';
	import type Hls from 'hls.js';
	import PlayIcon from 'phosphor-svelte/lib/PlayIcon';
	import PauseIcon from 'phosphor-svelte/lib/PauseIcon';
	import ArrowCounterClockwiseIcon from 'phosphor-svelte/lib/ArrowCounterClockwiseIcon';
	import ArrowClockwiseIcon from 'phosphor-svelte/lib/ArrowClockwiseIcon';
	import GaugeIcon from 'phosphor-svelte/lib/GaugeIcon';
	import XIcon from 'phosphor-svelte/lib/XIcon';
	import LockSimpleIcon from 'phosphor-svelte/lib/LockSimpleIcon';
	import LockSimpleOpenIcon from 'phosphor-svelte/lib/LockSimpleOpenIcon';
	import StackIcon from 'phosphor-svelte/lib/StackIcon';
	import SkipForwardIcon from 'phosphor-svelte/lib/SkipForwardIcon';
	import SubtitlesIcon from 'phosphor-svelte/lib/SubtitlesIcon';
	import CheckIcon from 'phosphor-svelte/lib/CheckIcon';
	import CardsThreeIcon from 'phosphor-svelte/lib/CardsThreeIcon';
	import UsersThreeIcon from 'phosphor-svelte/lib/UsersThreeIcon';
	import { page } from '$app/state';
	import {
		expectedPosition,
		formatRoomCode,
		needsSeek,
		catchUpRate,
		type SharedSource
	} from '@neoworks-dev/otter-app/together';
	import { together, type RemotePlayback } from '$lib/stores/together.svelte';
	import PeerAvatar from '$lib/components/together/PeerAvatar.svelte';
	import TogetherPanel from '$lib/components/together/TogetherPanel.svelte';
	import TogetherChat from '$lib/components/together/TogetherChat.svelte';
	import TogetherOverlay from '$lib/components/together/TogetherOverlay.svelte';
	import { activeProfile } from '@neoworks-dev/otter-app/profile';
	import { fetchResumePosition, recordProgress } from '@neoworks-dev/otter-app/discovery';
	import { isNativeApp } from '$lib/server';
	import Scrubber from './Scrubber.svelte';
	import RatingTrio from './RatingTrio.svelte';
	import PlayerSheet from './PlayerSheet.svelte';
	import SpeedSlider from './SpeedSlider.svelte';
	import TracksSheet, { type Track } from './TracksSheet.svelte';
	import EndScreen from './EndScreen.svelte';
	import EpisodesSheet, { type PlayerEpisode } from './EpisodesSheet.svelte';

	const SKIP_SECONDS = 10;
	const CONTROLS_HIDE_MS = 3500;
	const DOUBLE_TAP_MS = 300;
	// Progress is saved at most this often while playing, plus on pause and close.
	const SAVE_INTERVAL_SECONDS = 15;
	const SPEEDS = [0.5, 0.75, 1, 1.25, 1.5];
	// The frame preview seeks at most this often while scrubbing.
	const PREVIEW_SEEK_MS = 120;
	// Watch Together: the host's position goes out this often.
	const HEARTBEAT_MS = 1000;
	// After a sync jump, heartbeats wait this long so the stream can settle.
	const SEEK_SETTLE_MS = 2500;
	// A catch-up rate ends on its own when heartbeats stop arriving.
	const CATCH_UP_TIMEOUT_MS = 2500;
	// After pressing a control, heartbeats that crossed it on the way are ignored.
	const LOCAL_ACTION_GRACE_MS = 2000;
	// Buffering shorter than this doesn't pause the others.
	const STALL_REPORT_MS = 800;

	type Sheet = 'speed' | 'sources' | 'tracks' | 'episodes' | 'together' | null;

	interface Props {
		src: string;
		mimeType: string;
		title: string;
		/** movie:… or episode:…; enables resume, progress tracking and rating. */
		mediaId?: string;
		/** Other links for the title; switching keeps the playback position. */
		sources?: { id: string; label: string; plugin: string }[];
		currentSourceId?: string;
		/** The playing link; Watch Together guests play the same one. */
		sharedSource?: SharedSource | null;
		onsource?: (sourceId: string) => void;
		/** Shows "Next Ep." and the end screen's Next Episode when set. */
		onnext?: () => void;
		/** A series' episodes; enables the Episodes panel. */
		episodes?: PlayerEpisode[];
		onepisode?: (episodeId: string) => void;
		/** Shown under the spinner while there is no src yet. */
		status?: string;
		/** A failure from finding or resolving a source, before playback. */
		loadError?: string | null;
		onclose: () => void;
	}

	let {
		src,
		mimeType,
		title,
		mediaId,
		sources = [],
		currentSourceId,
		sharedSource = null,
		onsource,
		onnext,
		episodes = [],
		onepisode,
		status = 'Loading…',
		loadError = null,
		onclose
	}: Props = $props();

	let container = $state<HTMLDivElement>();
	let video = $state<HTMLVideoElement>();
	let previewVideo = $state<HTMLVideoElement>();
	let playing = $state(false);
	let buffering = $state(true);
	let ended = $state(false);
	let error = $state<string | null>(null);
	let currentTime = $state(0);
	let duration = $state(0);
	let bufferedEnd = $state(0);
	let speed = $state(1);
	let sheet = $state<Sheet>(null);
	let locked = $state(false);
	let controlsVisible = $state(true);
	let previewTime = $state<number | null>(null);
	// Brief "-10s" / "+10s" bubble after a double tap.
	let skipFeedback = $state<{ side: 'left' | 'right'; key: number } | null>(null);

	let hls = $state<Hls | null>(null);
	let audioTracks = $state<Track[]>([]);
	let subtitleTracks = $state<Track[]>([]);
	let audioIndex = $state(0);
	let subtitleIndex = $state(-1);

	let hideTimer: ReturnType<typeof setTimeout> | undefined;
	let singleTapTimer: ReturnType<typeof setTimeout> | undefined;
	let lastTapTime = 0;
	let lastPreviewSeek = 0;
	let lastSavedAt = 0;
	let resumeAt = 0;
	let localActionUntil = 0;
	let settlingUntil = 0;
	// Watch Together nudges the speed by a few percent to close small drift.
	let catchUpFactor = 1;
	let catchUpTimer: ReturnType<typeof setTimeout> | undefined;
	// Watch Together: whether the group wants playback running. The video stays
	// paused (holding) until everyone has loaded the title.
	let wantPlaying = $state(true);
	let waitingFor = $derived(together.holdingFor(mediaId));
	let holding = $derived(waitingFor.length > 0);
	let wasHolding = false;
	let togetherView = $state<'chat' | 'room'>('chat');
	let stateReportTimer: ReturnType<typeof setTimeout> | undefined;

	let remaining = $derived(Math.max(0, duration - currentTime));
	let shownError = $derived(loadError || error);
	let hasTrackChoice = $derived(audioTracks.length > 1 || subtitleTracks.length > 0);
	let previewPercent = $derived(percentOf(previewTime));

	function percentOf(seconds: number | null): number {
		if (seconds === null || !duration) return 0;
		return (seconds / duration) * 100;
	}

	function formatTime(seconds: number): string {
		if (!isFinite(seconds)) return '0:00';
		const hours = Math.floor(seconds / 3600);
		const minutes = Math.floor((seconds % 3600) / 60);
		const secs = Math.floor(seconds % 60)
			.toString()
			.padStart(2, '0');
		if (hours > 0) return `${hours}:${minutes.toString().padStart(2, '0')}:${secs}`;
		return `${minutes}:${secs}`;
	}

	function playLabel(): string {
		if (playing) return 'Pause';
		return 'Play';
	}

	// Controls ---------------------------------------------------------------

	function scheduleHide() {
		clearTimeout(hideTimer);
		hideTimer = setTimeout(() => {
			if (playing && sheet === null && previewTime === null) controlsVisible = false;
		}, CONTROLS_HIDE_MS);
	}

	function showControls() {
		controlsVisible = true;
		scheduleHide();
	}

	function toggleControls() {
		if (controlsVisible) {
			controlsVisible = false;
			return;
		}
		showControls();
	}

	function togglePlay() {
		if (!video) return;
		wantPlaying = !intendedPlaying();
		applyIntent();
		announceLocalAction();
		showControls();
	}

	// While Watch Together holds, the video is paused but may be meant to play.
	function intendedPlaying(): boolean {
		if (holding || !video) return wantPlaying;
		return !video.paused;
	}

	function applyIntent() {
		if (!video || !src) return;
		if (!wantPlaying || holding) {
			video.pause();
			return;
		}
		if (video.paused && !ended) video.play().catch(() => {});
	}

	function skip(seconds: number) {
		if (!video) return;
		video.currentTime = Math.min(duration, Math.max(0, video.currentTime + seconds));
		announceLocalAction();
		showControls();
	}

	function seekTo(seconds: number) {
		if (!video) return;
		video.currentTime = seconds;
		announceLocalAction();
		showControls();
	}

	function openSheet(next: Sheet) {
		sheet = next;
		controlsVisible = true;
	}

	function closeSheet() {
		sheet = null;
		showControls();
	}

	function chooseSpeed(value: number) {
		speed = value;
		if (video) video.playbackRate = value * catchUpFactor;
	}

	function chooseSource(sourceId: string) {
		sheet = null;
		if (sourceId === currentSourceId) return;
		// Continue the new link where the old one was.
		resumeAt = video?.currentTime || 0;
		onsource?.(sourceId);
	}

	function chooseEpisode(episodeId: string) {
		sheet = null;
		if (episodeId === mediaId) return;
		saveProgress(true);
		onepisode?.(episodeId);
	}

	function playNext() {
		saveProgress(true);
		onnext?.();
	}

	function toggleLock() {
		locked = !locked;
		sheet = null;
		showControls();
	}

	// A single tap toggles the controls; a double tap on a side skips.
	function onSurfaceTap(event: MouseEvent) {
		if (locked) {
			toggleControls();
			return;
		}
		const now = Date.now();
		const isDoubleTap = now - lastTapTime < DOUBLE_TAP_MS;
		lastTapTime = now;
		if (!isDoubleTap) {
			singleTapTimer = setTimeout(toggleControls, DOUBLE_TAP_MS);
			return;
		}
		clearTimeout(singleTapTimer);
		const side = sideOf(event.clientX);
		if (side === 'left') skip(-SKIP_SECONDS);
		else skip(SKIP_SECONDS);
		skipFeedback = { side, key: now };
	}

	function sideOf(clientX: number): 'left' | 'right' {
		if (clientX < window.innerWidth / 2) return 'left';
		return 'right';
	}

	// Scrubbing shows the time under the finger and, for direct files, that frame.
	function onPreview(seconds: number | null) {
		previewTime = seconds;
		controlsVisible = true;
		if (seconds === null) {
			scheduleHide();
			return;
		}
		const now = Date.now();
		if (!previewVideo || now - lastPreviewSeek < PREVIEW_SEEK_MS) return;
		lastPreviewSeek = now;
		previewVideo.currentTime = seconds;
	}

	// Progress ---------------------------------------------------------------

	function saveProgress(force = false) {
		const profile = get(activeProfile);
		if (!mediaId || !video || !profile) return;
		const total = video.duration;
		// Progress needs a known duration (live streams have none).
		if (!isFinite(total) || total <= 0) return;
		if (!force && Math.abs(video.currentTime - lastSavedAt) < SAVE_INTERVAL_SECONDS) return;
		lastSavedAt = video.currentTime;
		recordProgress({
			profileId: profile.id,
			mediaId,
			progressSeconds: video.currentTime,
			durationSeconds: total
		});
	}

	function onLoadedMetadata() {
		duration = video?.duration || 0;
		if (video) video.playbackRate = speed;
		if (video && resumeAt > 0 && resumeAt < duration) video.currentTime = resumeAt;
	}

	function onTimeUpdate() {
		if (!video) return;
		currentTime = video.currentTime;
		if (video.buffered.length > 0) bufferedEnd = video.buffered.end(video.buffered.length - 1);
		saveProgress();
	}

	function onEnded() {
		saveProgress(true);
		ended = true;
		controlsVisible = false;
	}

	function close() {
		saveProgress(true);
		onclose();
	}

	// Audio and subtitle tracks (HLS only) ------------------------------------

	function trackLabel(
		name: string | undefined,
		language: string | undefined,
		index: number
	): string {
		if (name) return name;
		if (language) return language;
		return `Track ${index + 1}`;
	}

	function readTracks(player: Hls) {
		audioTracks = player.audioTracks.map((track, index) => ({
			index,
			label: trackLabel(track.name, track.lang, index)
		}));
		subtitleTracks = player.subtitleTracks.map((track, index) => ({
			index,
			label: trackLabel(track.name, track.lang, index)
		}));
		audioIndex = Math.max(0, player.audioTrack);
		subtitleIndex = player.subtitleTrack;
	}

	function chooseAudio(index: number) {
		if (hls) hls.audioTrack = index;
		audioIndex = index;
	}

	function chooseSubtitle(index: number) {
		if (hls) {
			hls.subtitleDisplay = index >= 0;
			hls.subtitleTrack = index;
		}
		subtitleIndex = index;
	}

	// Source loading ---------------------------------------------------------

	// After a source switch, the position of the previous link; otherwise the
	// profile's saved progress.
	function resumePosition(): Promise<number> {
		if (resumeAt > 0) return Promise.resolve(resumeAt);
		const profile = get(activeProfile);
		if (!mediaId || !profile) return Promise.resolve(0);
		return fetchResumePosition(profile.id, mediaId);
	}

	function isHls(): boolean {
		return mimeType.includes('mpegURL') || src.includes('.m3u8');
	}

	async function attachHls(element: HTMLVideoElement, url: string): Promise<Hls | null> {
		const { default: HlsPlayer } = await import('hls.js');
		if (!HlsPlayer.isSupported()) {
			element.src = url;
			return null;
		}
		const player = new HlsPlayer();
		player.loadSource(url);
		player.attachMedia(element);
		player.on(HlsPlayer.Events.MANIFEST_PARSED, () => readTracks(player));
		player.on(HlsPlayer.Events.AUDIO_TRACKS_UPDATED, () => readTracks(player));
		player.on(HlsPlayer.Events.SUBTITLE_TRACKS_UPDATED, () => readTracks(player));
		player.on(HlsPlayer.Events.ERROR, (_, data) => {
			if (data.fatal) error = 'This source stopped working.';
		});
		return player;
	}

	// A new episode starts from its own saved position, not the previous one's.
	$effect.pre(() => {
		if (!mediaId) return;
		resumeAt = 0;
		lastSavedAt = 0;
		ended = false;
		currentTime = 0;
		duration = 0;
	});

	$effect(() => {
		const element = video;
		const url = src;
		if (!element) return;
		// The page is still finding or resolving a source.
		if (!url) {
			element.pause();
			buffering = true;
			return;
		}
		let attached: Hls | null = null;
		let cancelled = false;
		resumePosition().then(async (position) => {
			if (cancelled) return;
			resumeAt = position;
			buffering = true;
			ended = false;
			error = null;
			audioTracks = [];
			subtitleTracks = [];
			if (isHls()) attached = await attachHls(element, url);
			else element.src = url;
			hls = attached;
			wantPlaying = true;
			applyIntent();
		});
		return () => {
			cancelled = true;
			attached?.destroy();
		};
	});

	// Watch Together ------------------------------------------------------------

	function announceLocalAction() {
		localActionUntil = Date.now() + LOCAL_ACTION_GRACE_MS;
		together.announcePlayback();
	}

	// Path that opens this title for the others; episodes open on their series page.
	function shareRoute(id: string): string {
		if (id.startsWith('episode:')) return `${page.url.pathname}?episode=${encodeURIComponent(id)}`;
		return page.url.pathname;
	}

	// Reports the title once a source plays, so the others open it too.
	$effect(() => {
		const id = mediaId;
		if (!id || !src) return;
		together.attachPlayer({
			media: { type: 'media', mediaId: id, route: shareRoute(id), title, source: sharedSource },
			playing: () => wantPlaying,
			position: () => video?.currentTime || 0
		});
	});

	$effect(() => {
		const id = mediaId;
		if (!id) return;
		return () => together.detachPlayer(id);
	});

	// Tells the others whether this player is ready; brief stalls aren't reported.
	$effect(() => {
		const id = mediaId;
		const loading = buffering || !src;
		clearTimeout(stateReportTimer);
		if (!id) return;
		if (!loading) {
			together.announcePlayerState(id, false);
			return;
		}
		stateReportTimer = setTimeout(() => together.announcePlayerState(id, true), STALL_REPORT_MS);
	});

	// Pauses while someone is still loading, resumes once everyone is ready; the
	// host then sends its position so everyone starts from the same point.
	$effect(() => {
		const isHolding = holding;
		untrack(applyIntent);
		if (wasHolding && !isHolding && together.room?.hosting) together.announcePlayback();
		wasHolding = isHolding;
	});

	// Follows the others' play, pause and seeks; waits for the metadata first.
	$effect(() => {
		const remote = together.remotePlayback;
		if (!remote || !video || !duration || remote.payload.mediaId !== mediaId) return;
		if (remote.payload.heartbeat && Date.now() < localActionUntil) return;
		const target = remoteTarget(remote);
		untrack(() => {
			syncPosition(target, remote.payload.heartbeat);
			followPlaying(remote.payload.playing);
		});
	});

	// Jumps past 1s of drift; closes smaller drift by playing a bit faster or slower.
	function syncPosition(target: number, heartbeat: boolean) {
		if (!video) return;
		if (heartbeat && (buffering || Date.now() < settlingUntil)) return;
		if (needsSeek(video.currentTime, target)) {
			video.currentTime = target;
			settlingUntil = Date.now() + SEEK_SETTLE_MS;
			setCatchUp(1);
			return;
		}
		setCatchUp(catchUpRate(video.currentTime, target));
	}

	function setCatchUp(factor: number) {
		catchUpFactor = factor;
		if (video) video.playbackRate = speed * factor;
		clearTimeout(catchUpTimer);
		if (factor !== 1) catchUpTimer = setTimeout(() => setCatchUp(1), CATCH_UP_TIMEOUT_MS);
	}

	// Nobody advances while holding, so the sent position is still current.
	function remoteTarget(remote: RemotePlayback): number {
		if (holding) return remote.payload.position;
		return expectedPosition(remote.payload, remote.receivedAt, Date.now());
	}

	function followPlaying(shouldPlay: boolean) {
		wantPlaying = shouldPlay;
		applyIntent();
	}

	function sendHeartbeat() {
		if (!together.room?.hosting || !duration || holding) return;
		together.announcePlayback(true);
	}

	// The Watch Together side pane shows the chat, or the room (code, people, Leave).
	function nextTogetherView(): 'chat' | 'room' {
		if (togetherView === 'chat') return 'room';
		return 'chat';
	}

	function togetherViewLabel(): string {
		if (togetherView === 'room') return 'Chat';
		return formatRoomCode(together.room?.code || '');
	}

	function startWithoutWaiting() {
		if (mediaId) together.startWithoutWaiting(mediaId);
	}

	function waitingText(): string {
		const names = waitingFor.map((peer) => peer.name);
		if (names.length <= 2) return `Waiting for ${names.join(' and ')}…`;
		return `Waiting for ${names.length} people…`;
	}

	// Fullscreen, landscape and the Android back button -----------------------

	onMount(() => {
		container?.requestFullscreen?.().catch(() => {});
		let removeBackListener: (() => void) | null = null;
		if (isNativeApp()) {
			ScreenOrientation.lock({ orientation: 'landscape' }).catch(() => {});
			App.addListener('backButton', onBackButton).then((handle) => {
				removeBackListener = () => handle.remove();
			});
		}
		showControls();
		const heartbeatTimer = setInterval(sendHeartbeat, HEARTBEAT_MS);
		return () => {
			clearInterval(heartbeatTimer);
			clearTimeout(catchUpTimer);
			clearTimeout(hideTimer);
			clearTimeout(singleTapTimer);
			removeBackListener?.();
			// The app outside the player is portrait only.
			if (isNativeApp()) ScreenOrientation.lock({ orientation: 'portrait' }).catch(() => {});
			if (document.fullscreenElement) document.exitFullscreen().catch(() => {});
		};
	});

	// Back closes an open sheet first, then the player.
	function onBackButton() {
		if (sheet !== null) {
			closeSheet();
			return;
		}
		close();
	}
</script>

<div
	bind:this={container}
	class="fixed inset-0 z-[60] overflow-hidden bg-black text-white"
	role="presentation"
>
	<!-- svelte-ignore a11y_media_has_caption -->
	<video
		bind:this={video}
		class="absolute inset-0 h-full w-full"
		playsinline
		crossorigin="anonymous"
		onplay={() => {
			playing = true;
			wantPlaying = true;
			ended = false;
			scheduleHide();
		}}
		onpause={() => {
			playing = false;
			if (!holding) wantPlaying = false;
			controlsVisible = true;
			saveProgress(true);
		}}
		onwaiting={() => (buffering = true)}
		onplaying={() => (buffering = false)}
		oncanplay={() => (buffering = false)}
		onloadedmetadata={onLoadedMetadata}
		ontimeupdate={onTimeUpdate}
		onended={onEnded}
		onerror={() => (error = 'This source could not be played.')}
	></video>

	<!-- Tap surface under the controls: toggles them, double tap skips. -->
	<button
		data-backdrop
		class="absolute inset-0 cursor-default"
		aria-label="Toggle controls"
		onclick={onSurfaceTap}
	></button>

	{#if skipFeedback}
		{#key skipFeedback.key}
			<div
				class="skip-feedback glass-bar pointer-events-none absolute top-1/2 flex h-20 w-20 -translate-y-1/2 flex-col items-center justify-center rounded-full text-sm font-semibold"
				class:left-[15%]={skipFeedback.side === 'left'}
				class:right-[15%]={skipFeedback.side === 'right'}
			>
				{#if skipFeedback.side === 'left'}
					<ArrowCounterClockwiseIcon size={24} />−{SKIP_SECONDS}s
				{:else}
					<ArrowClockwiseIcon size={24} />+{SKIP_SECONDS}s
				{/if}
			</div>
		{/key}
	{/if}

	{#if holding && wantPlaying && src && !shownError && !ended}
		<div
			class="absolute inset-0 z-[6] flex items-center justify-center"
			transition:fade={{ duration: 200 }}
		>
			<div
				class="glass-bar flex flex-col items-center gap-3 rounded-3xl px-6 py-5"
				in:scale={{ start: 0.9, duration: 280, easing: backOut }}
			>
				<div class="flex -space-x-2">
					{#each waitingFor as peer (peer.id)}
						<PeerAvatar {peer} size={40} buffering />
					{/each}
				</div>
				<p class="text-sm font-semibold">{waitingText()}</p>
				<button
					class="rounded-full bg-white/10 px-4 py-2 text-xs font-semibold"
					onclick={startWithoutWaiting}
				>
					Start without them
				</button>
			</div>
		</div>
	{:else if buffering && !shownError && !ended}
		<div
			class="pointer-events-none absolute inset-0 flex flex-col items-center justify-center gap-3"
		>
			<span class="loading loading-spinner loading-lg text-white/60"></span>
			{#if !src}<span class="text-sm text-white/70">{status}</span>{/if}
		</div>
	{/if}

	{#if shownError}
		<div class="absolute inset-0 z-10 flex flex-col items-center justify-center gap-4">
			<p class="text-sm text-white/80">{shownError}</p>
			<button
				class="glass-bar rounded-full px-5 py-2 text-sm font-semibold"
				onclick={() => {
					if (sources.length > 1 && onsource) openSheet('sources');
					else close();
				}}
			>
				Choose another source
			</button>
		</div>
	{/if}

	{#if locked}
		<div
			class="controls pointer-events-none absolute inset-0"
			class:controls-hidden={!controlsVisible}
		>
			<div class="absolute inset-x-0 bottom-[calc(var(--safe-bottom)+24px)] flex justify-center">
				<button
					class="glass-bar pointer-events-auto flex h-11 items-center gap-2 rounded-full px-5 text-sm font-semibold"
					onclick={toggleLock}
				>
					<LockSimpleOpenIcon size={18} />
					Unlock
				</button>
			</div>
		</div>
	{:else if !ended}
		<div
			class="controls pointer-events-none absolute inset-0"
			class:controls-hidden={!controlsVisible}
			class:controls-scrubbing={previewTime !== null}
		>
			<div
				class="fade-while-scrubbing absolute inset-x-0 top-0 flex items-start justify-between gap-3 bg-gradient-to-b from-black/70 to-transparent px-5 pt-[calc(var(--safe-top)+14px)] pb-12"
			>
				<span class="min-w-0 flex-1 truncate pt-2 text-base font-semibold">{title}</span>
				{#if mediaId}
					<div class="pointer-events-auto">
						<RatingTrio {mediaId} oninteract={showControls} />
					</div>
				{/if}
				<div class="pointer-events-auto flex flex-1 items-center justify-end gap-2">
					{#if together.others.length > 0}
						<button
							class="flex items-center -space-x-2 pr-1"
							aria-label="Watch Together"
							onclick={() => openSheet('together')}
						>
							{#each together.others as peer (peer.id)}
								<PeerAvatar {peer} size={30} buffering={together.isBuffering(peer.id)} />
							{/each}
						</button>
					{/if}
					<button
						class="flex h-11 w-11 items-center justify-center"
						aria-label="Lock controls"
						onclick={toggleLock}
					>
						<LockSimpleIcon size={24} />
					</button>
					<button
						class="flex h-11 w-11 items-center justify-center"
						aria-label="Close player"
						onclick={close}
					>
						<XIcon size={26} />
					</button>
				</div>
			</div>

			{#if !buffering && !shownError && !holding}
				<div class="fade-while-scrubbing absolute inset-0 flex items-center justify-center gap-20">
					<button
						class="center-control pointer-events-auto flex h-16 w-16 items-center justify-center"
						aria-label="Back {SKIP_SECONDS} seconds"
						onclick={() => skip(-SKIP_SECONDS)}
					>
						<ArrowCounterClockwiseIcon size={44} />
						<span class="absolute text-[11px] font-bold">{SKIP_SECONDS}</span>
					</button>
					<button
						class="center-control pointer-events-auto flex h-20 w-20 items-center justify-center"
						aria-label={playLabel()}
						onclick={togglePlay}
					>
						{#if playing}<PauseIcon size={56} weight="fill" />{:else}<PlayIcon
								size={56}
								weight="fill"
							/>{/if}
					</button>
					<button
						class="center-control pointer-events-auto flex h-16 w-16 items-center justify-center"
						aria-label="Forward {SKIP_SECONDS} seconds"
						onclick={() => skip(SKIP_SECONDS)}
					>
						<ArrowClockwiseIcon size={44} />
						<span class="absolute text-[11px] font-bold">{SKIP_SECONDS}</span>
					</button>
				</div>
			{/if}

			<div
				class="absolute inset-x-0 bottom-0 flex flex-col gap-1 bg-gradient-to-t from-black/80 to-transparent px-5 pt-12 pb-[calc(var(--safe-bottom)+10px)]"
			>
				<div class="pointer-events-auto relative flex items-center gap-3">
					<div class="relative flex-1">
						{#if previewTime !== null}
							<div
								class="scrub-preview pointer-events-none absolute bottom-10 flex -translate-x-1/2 flex-col items-center gap-2"
								style:left="{previewPercent}%"
							>
								{#if !isHls()}
									<video
										bind:this={previewVideo}
										{src}
										muted
										playsinline
										preload="auto"
										class="h-28 rounded-md bg-black object-cover shadow-xl"
									></video>
								{/if}
								<span class="text-xl font-semibold tabular-nums drop-shadow"
									>{formatTime(previewTime)}</span
								>
							</div>
						{/if}
						<Scrubber
							{currentTime}
							{duration}
							{bufferedEnd}
							onseek={seekTo}
							onpreview={onPreview}
						/>
					</div>
					<span class="w-16 text-right font-mono text-xs text-white/80 tabular-nums">
						{formatTime(remaining)}
					</span>
				</div>
				<div
					class="fade-while-scrubbing pointer-events-auto flex items-center justify-center gap-6"
				>
					<button
						class="flex h-10 items-center gap-2 px-2 text-xs font-semibold"
						onclick={() => openSheet('speed')}
					>
						<GaugeIcon size={20} />
						Speed ({speed}x)
					</button>
					{#if hasTrackChoice}
						<button
							class="flex h-10 items-center gap-2 px-2 text-xs font-semibold"
							onclick={() => openSheet('tracks')}
						>
							<SubtitlesIcon size={20} />
							Audio & Subtitles
						</button>
					{/if}
					{#if sources.length > 1 && onsource}
						<button
							class="flex h-10 items-center gap-2 px-2 text-xs font-semibold"
							onclick={() => openSheet('sources')}
						>
							<StackIcon size={20} />
							Sources
						</button>
					{/if}
					{#if episodes.length > 0 && onepisode}
						<button
							class="flex h-10 items-center gap-2 px-2 text-xs font-semibold"
							onclick={() => openSheet('episodes')}
						>
							<CardsThreeIcon size={20} />
							Episodes
						</button>
					{/if}
					{#if mediaId}
						<button
							class="flex h-10 items-center gap-2 px-2 text-xs font-semibold"
							onclick={() => openSheet('together')}
						>
							<UsersThreeIcon size={20} />
							Together
						</button>
					{/if}
					{#if onnext}
						<button
							class="flex h-10 items-center gap-2 px-2 text-xs font-semibold"
							onclick={playNext}
						>
							<SkipForwardIcon size={20} />
							Next Ep.
						</button>
					{/if}
				</div>
			</div>
		</div>
	{/if}

	{#if together.open}
		<TogetherOverlay />
	{/if}

	{#if ended}
		<EndScreen {title} {mediaId} {onnext} onclose={close} />
	{/if}

	{#if sheet === 'speed'}
		<PlayerSheet side onclose={closeSheet}>
			<div class="flex flex-1 flex-col justify-center gap-6 px-8">
				<h2 class="text-lg font-bold">Playback Speed</h2>
				<SpeedSlider speeds={SPEEDS} value={speed} onchange={chooseSpeed} />
			</div>
		</PlayerSheet>
	{:else if sheet === 'sources'}
		<PlayerSheet side onclose={closeSheet}>
			<div class="flex min-h-0 flex-1 flex-col gap-3 px-6 pt-6 pb-6">
				<h2 class="text-lg font-bold">Sources</h2>
				<div class="no-scrollbar flex flex-col overflow-y-auto">
					{#each sources as source (source.id)}
						<button
							class="flex items-center gap-3 py-2.5 text-left text-sm {source.id === currentSourceId
								? 'font-bold text-white'
								: 'text-white/55'}"
							onclick={() => chooseSource(source.id)}
						>
							<span class="w-5 shrink-0"
								>{#if source.id === currentSourceId}<CheckIcon size={18} />{/if}</span
							>
							<span class="min-w-0 flex-1 truncate">{source.label}</span>
							<span
								class="shrink-0 rounded-full bg-white/10 px-2 py-0.5 text-[11px] font-medium text-white/70"
							>
								{source.plugin}
							</span>
						</button>
					{/each}
				</div>
			</div>
		</PlayerSheet>
	{:else if sheet === 'episodes' && onepisode}
		<EpisodesSheet
			{episodes}
			currentEpisodeId={mediaId}
			onepisode={chooseEpisode}
			onclose={closeSheet}
		/>
	{:else if sheet === 'together'}
		<PlayerSheet side onclose={closeSheet}>
			<div class="flex min-h-0 flex-1 flex-col gap-3 px-5 pt-5 pb-4">
				{#if together.room && together.open}
					<div class="flex h-10 items-center gap-2 pr-12">
						<div class="flex -space-x-2">
							{#each together.room.peers as peer (peer.id)}
								<PeerAvatar {peer} size={28} buffering={together.isBuffering(peer.id)} />
							{/each}
						</div>
						<button
							class="ml-auto h-8 rounded-full bg-white/8 px-3 font-mono text-xs font-semibold tracking-wider"
							onclick={() => (togetherView = nextTogetherView())}
						>
							{togetherViewLabel()}
						</button>
					</div>
					{#if togetherView === 'chat'}
						<TogetherChat />
					{:else}
						<div class="no-scrollbar min-h-0 flex-1 overflow-y-auto">
							<TogetherPanel />
						</div>
					{/if}
				{:else}
					<div class="no-scrollbar min-h-0 flex-1 overflow-y-auto pt-6">
						<TogetherPanel />
					</div>
				{/if}
			</div>
		</PlayerSheet>
	{:else if sheet === 'tracks'}
		<TracksSheet
			{audioTracks}
			{subtitleTracks}
			{audioIndex}
			{subtitleIndex}
			onaudio={chooseAudio}
			onsubtitle={chooseSubtitle}
			onclose={closeSheet}
		/>
	{/if}
</div>

<style>
	.controls {
		transition: opacity 220ms ease-out;
	}

	.controls-hidden {
		opacity: 0;
	}

	.controls-hidden :global(*) {
		pointer-events: none !important;
	}

	/* While scrubbing only the progress bar and its preview stay visible. */
	.fade-while-scrubbing {
		transition: opacity 180ms ease-out;
	}

	.controls-scrubbing .fade-while-scrubbing {
		opacity: 0;
	}

	/* Plain icons like Netflix; a shadow keeps them readable on bright video. */
	.center-control {
		position: relative;
		filter: drop-shadow(0 2px 8px rgb(0 0 0 / 0.5));
	}

	.skip-feedback {
		animation: skip-pop 600ms ease-out forwards;
	}

	@keyframes skip-pop {
		0% {
			opacity: 0;
			scale: 0.8;
		}
		20% {
			opacity: 1;
			scale: 1;
		}
		100% {
			opacity: 0;
			scale: 1;
		}
	}
</style>
