<script lang="ts">
	// 16:9 banner at the top of a title page: the backdrop, replaced by a muted
	// looping trailer once it has had a moment to load, with a mute button.
	// The media drifts slower than the page while scrolling (parallax).
	import SpeakerSlashIcon from 'phosphor-svelte/lib/SpeakerSlashIcon';
	import SpeakerHighIcon from 'phosphor-svelte/lib/SpeakerHighIcon';
	import { watchScrollTop } from '$lib/scroll';

	// Keeps the backdrop up while YouTube's player boots behind it.
	const TRAILER_REVEAL_DELAY_MS = 1800;
	// Share of the scroll distance the media lags behind the page.
	const PARALLAX_FACTOR = 0.45;

	interface Props {
		imageUrl: string | null;
		trailerKey: string | null;
		title: string;
	}

	let { imageUrl, trailerKey, title }: Props = $props();

	let trailerFrame = $state<HTMLIFrameElement>();
	let trailerVisible = $state(false);
	let muted = $state(true);
	let banner = $state<HTMLDivElement>();
	let scrollTop = $state(0);

	$effect(() => {
		if (!banner) return;
		return watchScrollTop(banner, (top) => (scrollTop = Math.max(0, top)));
	});

	function trailerEmbedUrl(key: string): string {
		const params = `autoplay=1&mute=1&loop=1&playlist=${key}&controls=0&modestbranding=1&rel=0&playsinline=1&enablejsapi=1`;
		return `https://www.youtube-nocookie.com/embed/${key}?${params}`;
	}

	$effect(() => {
		if (!trailerKey) return;
		trailerVisible = false;
		const timer = setTimeout(() => (trailerVisible = true), TRAILER_REVEAL_DELAY_MS);
		return () => clearTimeout(timer);
	});

	function muteLabel(): string {
		if (muted) return 'Unmute trailer';
		return 'Mute trailer';
	}

	// The embed is controlled through the YouTube iframe API's postMessage commands.
	function toggleMute() {
		muted = !muted;
		let command = 'unMute';
		if (muted) command = 'mute';
		const message = JSON.stringify({ event: 'command', func: command, args: [] });
		trailerFrame?.contentWindow?.postMessage(message, '*');
	}
</script>

<div bind:this={banner} class="relative -mx-3 aspect-video overflow-hidden bg-black">
	<div
		class="absolute inset-0 will-change-transform"
		style:transform="translate3d(0, {scrollTop * PARALLAX_FACTOR}px, 0)"
	>
		{#if trailerKey}
			<iframe
				bind:this={trailerFrame}
				src={trailerEmbedUrl(trailerKey)}
				title="{title} trailer"
				class="pointer-events-none absolute top-1/2 left-1/2 aspect-video w-[135%] -translate-x-1/2 -translate-y-1/2"
				allow="autoplay; encrypted-media"
			></iframe>
		{/if}
		{#if imageUrl}
			<img
				src={imageUrl}
				alt=""
				class="absolute inset-0 h-full w-full object-cover transition-opacity duration-700"
				class:opacity-0={trailerVisible}
			/>
		{/if}
	</div>
	<div
		class="from-base-100 pointer-events-none absolute inset-x-0 bottom-0 h-1/3 bg-gradient-to-t"
	></div>

	{#if trailerKey && trailerVisible}
		<button
			class="glass-bar absolute right-3 bottom-3 flex h-9 w-9 items-center justify-center rounded-full"
			aria-label={muteLabel()}
			onclick={toggleMute}
		>
			{#if muted}<SpeakerSlashIcon size={18} />{:else}<SpeakerHighIcon size={18} />{/if}
		</button>
	{/if}
</div>
