<script lang="ts">
	import XIcon from 'phosphor-svelte/lib/XIcon';
	import PlayIcon from 'phosphor-svelte/lib/PlayIcon';
	import PauseIcon from 'phosphor-svelte/lib/PauseIcon';
	import SpeakerHighIcon from 'phosphor-svelte/lib/SpeakerHighIcon';
	import SpeakerXIcon from 'phosphor-svelte/lib/SpeakerXIcon';
	import ArrowsOutIcon from 'phosphor-svelte/lib/ArrowsOutIcon';
	import SubtitlesIcon from 'phosphor-svelte/lib/SubtitlesIcon';
	import WaveformIcon from 'phosphor-svelte/lib/WaveformIcon';
	import CheckIcon from 'phosphor-svelte/lib/CheckIcon';
	import type Hls from 'hls.js';
	import { get } from 'svelte/store';
	import { activeProfile, type Profile } from '@neoworks-dev/otter-app/profile';
	import { recordProgress } from '@neoworks-dev/otter-app/discovery';
	import {
		captionLanguageOf,
		captionPreferencesOf,
		defaultCaptionKey,
		embeddedTrackLabel,
		twoLetterLanguage,
		type CaptionTrack
	} from '@neoworks-dev/otter-app/captions';
	import {
		fetchSubtitles,
		saveCaptionPreferences,
		subtitleFileUrl,
		type CaptionPreferenceChange,
		type Subtitle
	} from '@neoworks-dev/otter-app/subtitles';
	import { createVoiceBoost, type VoiceBoost } from '$lib/voiceBoost';

	interface Props {
		src: string;
		mimeType: string;
		title: string;
		// Media record id (movie:… / episode:…). When set, playback progress is
		// recorded for resume + recommendations.
		mediaId?: string;
		// Inline players embed in the page (aspect-video box) instead of taking
		// over the viewport as a fullscreen modal.
		inline?: boolean;
		onclose: () => void;
	}

	let {
		src,
		mimeType,
		title,
		mediaId,
		inline = false,
		onclose
	}: Props = $props();

	// Progress is saved at most every 15s while playing, plus on pause/close.
	const SAVE_INTERVAL_SECONDS = 15;
	let lastSavedAt = 0;

	function saveProgress(force = false) {
		if (!mediaId || !videoEl) return;
		const profile = get(activeProfile);
		if (!profile) return;
		const position = videoEl.currentTime;
		const total = videoEl.duration;
		// Progress needs a known duration (live streams have none).
		if (!isFinite(total) || total <= 0) return;
		if (!force && position - lastSavedAt < SAVE_INTERVAL_SECONDS) return;
		lastSavedAt = position;
		recordProgress({
			profileId: profile.id,
			mediaId,
			progressSeconds: position,
			durationSeconds: total
		});
	}

	function close() {
		saveProgress(true);
		onclose();
	}

	let videoEl = $state<HTMLVideoElement | undefined>();
	let containerEl = $state<HTMLDivElement | undefined>();

	let playing = $state(false);
	let currentTime = $state(0);
	let duration = $state(0);
	let volume = $state(1);
	let muted = $state(false);
	let buffering = $state(true);
	let error = $state<string | null>(null);
	let controlsVisible = $state(true);
	let hideTimer: ReturnType<typeof setTimeout>;

	function showControls() {
		controlsVisible = true;
		clearTimeout(hideTimer);
		hideTimer = setTimeout(() => {
			if (playing && !captionMenuOpen) controlsVisible = false;
		}, 3000);
	}

	// Captions ------------------------------------------------------------------

	let preferences = $derived(captionPreferencesOf($activeProfile));
	let profileId = $derived(idOf($activeProfile));

	function idOf(profile: Profile | null): string | null {
		if (!profile) return null;
		return profile.id;
	}

	let externalSubtitles = $state<Subtitle[]>([]);
	let embeddedTracks = $state<CaptionTrack[]>([]);
	let selectedCaptionKey = $state<string | null>(null);
	let captionMenuOpen = $state(false);
	let captionMenuEl = $state<HTMLDivElement | undefined>();
	// Set once the viewer picks a track or Off; the profile default stops applying.
	let captionChosenByUser = false;
	let hlsPlayer: Hls | null = null;

	function externalTrackOf(subtitle: Subtitle): CaptionTrack {
		return {
			key: `external:${subtitle.id}`,
			kind: 'external',
			language: subtitle.language,
			label: subtitle.label,
			detail: subtitle.release
		};
	}

	let captionTracks = $derived<CaptionTrack[]>([
		...externalSubtitles.map(externalTrackOf),
		...embeddedTracks
	]);

	function readEmbeddedTracks(player: Hls) {
		embeddedTracks = player.subtitleTracks.map((track, index) => ({
			key: `embedded:${index}`,
			kind: 'embedded',
			language: twoLetterLanguage(track.lang),
			label: embeddedTrackLabel(track.name, track.lang, index),
			detail: 'In the stream'
		}));
	}

	function showExternalTrack(element: HTMLVideoElement, key: string | null) {
		const externalIds = new Set(externalSubtitles.map((subtitle) => subtitle.id));
		for (const textTrack of Array.from(element.textTracks)) {
			if (!externalIds.has(textTrack.id)) continue;
			textTrack.mode = 'disabled';
			if (key === `external:${textTrack.id}`) textTrack.mode = 'showing';
		}
	}

	function showEmbeddedTrack(player: Hls, key: string | null) {
		if (key === null || !key.startsWith('embedded:')) {
			player.subtitleDisplay = false;
			return;
		}
		player.subtitleTrack = Number(key.slice('embedded:'.length));
		player.subtitleDisplay = true;
	}

	function applyCaptionSelection(element: HTMLVideoElement, key: string | null) {
		showExternalTrack(element, key);
		if (hlsPlayer) showEmbeddedTrack(hlsPlayer, key);
	}

	function saveCaptionChange(change: CaptionPreferenceChange) {
		if (!profileId) return;
		saveCaptionPreferences(profileId, change).catch(() => {
			// The choice still holds for this playback.
		});
	}

	function chooseCaption(key: string | null) {
		captionChosenByUser = true;
		selectedCaptionKey = key;
		captionMenuOpen = false;
		if (key === null) {
			saveCaptionChange({ subtitlesEnabled: false });
			return;
		}
		const language = captionTracks.find((track) => track.key === key)?.language;
		if (language) saveCaptionChange({ subtitlesEnabled: true, subtitleLanguage: language });
		else saveCaptionChange({ subtitlesEnabled: true });
	}

	// The C key: Off when a track shows, otherwise the track the profile would get by default.
	function toggleCaptions() {
		if (selectedCaptionKey !== null) {
			chooseCaption(null);
			return;
		}
		const wanted = defaultCaptionKey(
			captionTracks,
			{ ...preferences, subtitlesEnabled: true },
			captionLanguageOf($activeProfile)
		);
		if (wanted) chooseCaption(wanted);
		else captionMenuOpen = captionTracks.length > 0;
	}

	function closeCaptionMenuOnOutsideClick(event: MouseEvent) {
		if (!captionMenuOpen) return;
		if (captionMenuEl?.contains(event.target as Node)) return;
		captionMenuOpen = false;
	}

	$effect(() => {
		const id = mediaId;
		const forProfile = profileId;
		externalSubtitles = [];
		if (!id) return;
		let cancelled = false;
		fetchSubtitles(id, forProfile)
			.then((subtitles) => {
				if (!cancelled) externalSubtitles = subtitles;
			})
			.catch(() => {
				// Playback goes on without external captions.
			});
		return () => {
			cancelled = true;
		};
	});

	$effect(() => {
		if (captionChosenByUser) return;
		selectedCaptionKey = defaultCaptionKey(
			captionTracks,
			preferences,
			captionLanguageOf($activeProfile)
		);
	});

	$effect(() => {
		const element = videoEl;
		const key = selectedCaptionKey;
		// Re-apply when tracks appear: <track> elements render after the list changes.
		void externalSubtitles;
		void embeddedTracks;
		if (element) applyCaptionSelection(element, key);
	});

	// Voice boost -----------------------------------------------------------------

	let voiceBoostOn = $state(captionPreferencesOf(get(activeProfile)).voiceBoost);
	let voiceBoost = $state<VoiceBoost | undefined>();

	function toggleVoiceBoost() {
		voiceBoostOn = !voiceBoostOn;
		saveCaptionChange({ voiceBoost: voiceBoostOn });
	}

	$effect(() => {
		if (!videoEl) return;
		const boost = createVoiceBoost(videoEl);
		voiceBoost = boost;
		return () => {
			boost.destroy();
			voiceBoost = undefined;
		};
	});

	$effect(() => {
		voiceBoost?.setEnabled(voiceBoostOn);
	});

	function fmtTime(s: number): string {
		if (!isFinite(s)) return '0:00';
		const h = Math.floor(s / 3600);
		const m = Math.floor((s % 3600) / 60);
		const sec = Math.floor(s % 60);
		if (h > 0) return `${h}:${m.toString().padStart(2, '0')}:${sec.toString().padStart(2, '0')}`;
		return `${m}:${sec.toString().padStart(2, '0')}`;
	}

	function togglePlay() {
		if (!videoEl) return;
		if (videoEl.paused) videoEl.play();
		else videoEl.pause();
	}

	function seek(e: Event) {
		if (!videoEl) return;
		videoEl.currentTime = Number((e.target as HTMLInputElement).value);
	}

	function setVolume(e: Event) {
		if (!videoEl) return;
		const v = Number((e.target as HTMLInputElement).value);
		videoEl.volume = v;
		videoEl.muted = false;
	}

	function toggleMute() {
		if (!videoEl) return;
		videoEl.muted = !videoEl.muted;
	}

	async function toggleFullscreen() {
		if (!containerEl) return;
		if (document.fullscreenElement) await document.exitFullscreen();
		else await containerEl.requestFullscreen();
	}

	function handleKeydown(e: KeyboardEvent) {
		// only intercept when no input is focused
		if (e.target instanceof HTMLInputElement) return;
		switch (e.key) {
			case ' ':
			case 'k':
				e.preventDefault();
				togglePlay();
				break;
			case 'f':
				e.preventDefault();
				toggleFullscreen();
				break;
			case 'm':
				e.preventDefault();
				toggleMute();
				break;
			case 'c':
				e.preventDefault();
				toggleCaptions();
				break;
			case 'Escape':
				if (captionMenuOpen) {
					captionMenuOpen = false;
					return;
				}
				close();
				return;
			case 'ArrowLeft':
				e.preventDefault();
				if (videoEl) videoEl.currentTime = Math.max(0, videoEl.currentTime - 10);
				break;
			case 'ArrowRight':
				e.preventDefault();
				if (videoEl) videoEl.currentTime = Math.min(duration, videoEl.currentTime + 10);
				break;
		}
		showControls();
	}

	function attachHls(element: HTMLVideoElement, HlsPlayer: typeof Hls): Hls {
		const player = new HlsPlayer();
		player.loadSource(src);
		player.attachMedia(element);
		player.on(HlsPlayer.Events.MANIFEST_PARSED, () => {
			readEmbeddedTracks(player);
			element.play().catch(() => {});
		});
		player.on(HlsPlayer.Events.SUBTITLE_TRACKS_UPDATED, () => readEmbeddedTracks(player));
		player.on(HlsPlayer.Events.ERROR, (_, data) => {
			if (data.fatal) error = data.details ?? 'HLS playback error';
		});
		return player;
	}

	$effect(() => {
		if (!videoEl) return;

		const isHLS = mimeType.includes('mpegURL') || src.includes('.m3u8');
		let destroyed = false;

		if (isHLS) {
			import('hls.js').then(({ default: HlsPlayer }) => {
				if (!videoEl || destroyed) return;
				if (HlsPlayer.isSupported()) {
					hlsPlayer = attachHls(videoEl, HlsPlayer);
				} else if (!window.MediaSource && videoEl.canPlayType('application/vnd.apple.mpegurl')) {
					// Safari / iOS: no MSE, native HLS is the only option
					videoEl.src = src;
					videoEl.play().catch(() => {});
				} else {
					error = 'HLS not supported in this browser (missing H.264 codec?)';
				}
			});
		} else {
			videoEl.src = src;
			videoEl.play().catch(() => {});
		}

		return () => {
			destroyed = true;
			hlsPlayer?.destroy();
			hlsPlayer = null;
			embeddedTracks = [];
		};
	});
</script>

<svelte:window onkeydown={handleKeydown} onclick={closeCaptionMenuOnOutsideClick} />

{#snippet captionOption(key: string | null, label: string, detail: string | null)}
	<button
		type="button"
		role="menuitemradio"
		aria-checked={selectedCaptionKey === key}
		class="flex items-center gap-2 rounded-md px-3 py-2 text-left hover:bg-white/10"
		onclick={() => chooseCaption(key)}
	>
		<span class="flex min-w-0 flex-1 flex-col">
			<span class="truncate">{label}</span>
			{#if detail}
				<span class="truncate text-xs text-white/50">{detail}</span>
			{/if}
		</span>
		{#if selectedCaptionKey === key}
			<CheckIcon size={14} weight="bold" />
		{/if}
	</button>
{/snippet}

<!-- Fullscreen: z-[60] renders above MediaOverlay (z-50). Inline: embeds in-page. -->
<div
	bind:this={containerEl}
	class="flex flex-col bg-black {inline
		? 'relative aspect-video w-full overflow-hidden rounded-xl'
		: 'fixed inset-0 z-[60]'}"
	onmousemove={showControls}
	role="presentation"
	class:cursor-none={!controlsVisible}
>
	<!-- Top bar: title + close -->
	<div
		class="pointer-events-none absolute inset-x-0 top-0 z-10 flex items-center justify-between bg-gradient-to-b from-black/70 to-transparent p-4 transition-opacity duration-300"
		class:opacity-0={!controlsVisible}
		class:opacity-100={controlsVisible}
	>
		<span class="max-w-[70%] truncate text-sm font-medium text-white/80">{title}</span>
		<button
			type="button"
			class="pointer-events-auto flex h-9 w-9 items-center justify-center rounded-full bg-white/10 text-white transition-colors hover:bg-white/20"
			onclick={close}
			aria-label="Close player"
		>
			<XIcon size={18} weight="bold" />
		</button>
	</div>

	<!-- Video -->
	<video
		bind:this={videoEl}
		class="h-full w-full"
		onclick={togglePlay}
		ondblclick={toggleFullscreen}
		onplay={() => {
			playing = true;
			buffering = false;
		}}
		onpause={() => {
			playing = false;
			saveProgress(true);
		}}
		onwaiting={() => (buffering = true)}
		onplaying={() => (buffering = false)}
		ontimeupdate={() => {
			currentTime = videoEl?.currentTime ?? 0;
			saveProgress();
		}}
		ondurationchange={() => (duration = videoEl?.duration ?? 0)}
		onvolumechange={() => {
			volume = videoEl?.volume ?? 1;
			muted = videoEl?.muted ?? false;
		}}
		playsinline
		crossorigin="anonymous"
		data-caption-size={preferences.subtitleSize}
	>
		{#each externalSubtitles as subtitle (subtitle.id)}
			<track
				kind="subtitles"
				id={subtitle.id}
				src={subtitleFileUrl(subtitle.vttUrl)}
				srclang={subtitle.language}
				label={subtitle.label}
			/>
		{/each}
	</video>

	<!-- Buffering spinner -->
	{#if buffering && !error}
		<div class="pointer-events-none absolute inset-0 flex items-center justify-center">
			<span class="loading loading-spinner loading-lg text-white/40"></span>
		</div>
	{/if}

	<!-- Error -->
	{#if error}
		<div class="pointer-events-none absolute inset-0 flex items-center justify-center">
			<p class="rounded-xl bg-black/70 px-6 py-4 text-sm text-error">{error}</p>
		</div>
	{/if}

	<!-- Controls bar -->
	<div
		class="pointer-events-none absolute inset-x-0 bottom-0 z-10 flex flex-col gap-3 bg-gradient-to-t from-black/80 to-transparent px-4 pb-5 pt-12 transition-opacity duration-300"
		class:opacity-0={!controlsVisible}
		class:opacity-100={controlsVisible}
	>
		<!-- Seek bar -->
		<input
			type="range"
			min="0"
			max={duration || 0}
			value={currentTime}
			step="0.5"
			class="pointer-events-auto range range-xs w-full accent-white"
			oninput={seek}
			aria-label="Seek"
		/>

		<div class="pointer-events-auto flex items-center gap-3">
			<!-- Play/Pause -->
			<button
				type="button"
				class="flex h-9 w-9 flex-none items-center justify-center rounded-full text-white hover:bg-white/10"
				onclick={togglePlay}
				aria-label={playing ? 'Pause' : 'Play'}
			>
				{#if playing}
					<PauseIcon size={20} weight="fill" />
				{:else}
					<PlayIcon size={20} weight="fill" />
				{/if}
			</button>

			<!-- Time -->
			<span class="font-mono text-xs tabular-nums text-white/60">
				{fmtTime(currentTime)} / {fmtTime(duration)}
			</span>

			<div class="flex-1"></div>

			<!-- Voice boost -->
			<button
				type="button"
				class="flex h-8 w-8 flex-none items-center justify-center rounded-full transition-colors {voiceBoostOn
					? 'bg-white text-black'
					: 'text-white/70 hover:text-white'}"
				onclick={toggleVoiceBoost}
				aria-label="Voice boost"
				aria-pressed={voiceBoostOn}
				title="Voice boost"
			>
				<WaveformIcon size={16} weight="bold" />
			</button>

			<!-- Captions -->
			<div class="relative" bind:this={captionMenuEl}>
				<button
					type="button"
					class="flex h-8 w-8 flex-none items-center justify-center rounded-full transition-colors {selectedCaptionKey !==
					null
						? 'bg-white text-black'
						: 'text-white/70 hover:text-white'}"
					onclick={() => (captionMenuOpen = !captionMenuOpen)}
					aria-label="Captions"
					aria-haspopup="menu"
					aria-expanded={captionMenuOpen}
					title="Captions (C)"
				>
					<SubtitlesIcon size={16} weight="bold" />
				</button>
				{#if captionMenuOpen}
					<div
						role="menu"
						class="absolute bottom-full right-0 mb-2 flex max-h-72 w-64 flex-col overflow-y-auto rounded-lg border border-white/10 bg-black/90 p-1 text-sm text-white"
					>
						{@render captionOption(null, 'Off', null)}
						{#each captionTracks as track (track.key)}
							{@render captionOption(track.key, track.label, track.detail)}
						{/each}
						{#if captionTracks.length === 0}
							<p class="px-3 py-2 text-xs text-white/50">No captions found</p>
						{/if}
					</div>
				{/if}
			</div>

			<!-- Volume -->
			<div class="flex items-center gap-2">
				<button
					type="button"
					class="flex h-8 w-8 flex-none items-center justify-center text-white/70 hover:text-white"
					onclick={toggleMute}
					aria-label={muted ? 'Unmute' : 'Mute'}
				>
					{#if muted || volume === 0}
						<SpeakerXIcon size={16} weight="fill" />
					{:else}
						<SpeakerHighIcon size={16} weight="fill" />
					{/if}
				</button>
				<input
					type="range"
					min="0"
					max="1"
					step="0.05"
					value={muted ? 0 : volume}
					class="range range-xs w-20 accent-white"
					oninput={setVolume}
					aria-label="Volume"
				/>
			</div>

			<!-- Fullscreen -->
			<button
				type="button"
				class="flex h-8 w-8 flex-none items-center justify-center text-white/70 hover:text-white"
				onclick={toggleFullscreen}
				aria-label="Toggle fullscreen"
			>
				<ArrowsOutIcon size={16} weight="bold" />
			</button>
		</div>
	</div>
</div>

<style>
	/* Cue size follows the profile's caption size. */
	video[data-caption-size='SMALL']::cue {
		font-size: 75%;
	}
	video[data-caption-size='MEDIUM']::cue {
		font-size: 100%;
	}
	video[data-caption-size='LARGE']::cue {
		font-size: 140%;
	}
</style>
