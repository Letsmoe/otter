<script lang="ts">
	// Shown when playback ends: title and rating, close, and for episodes a
	// "Next Episode" button that fills up and starts on its own.
	import { onMount } from 'svelte';
	import XIcon from 'phosphor-svelte/lib/XIcon';
	import PlayIcon from 'phosphor-svelte/lib/PlayIcon';
	import RatingTrio from './RatingTrio.svelte';

	const NEXT_COUNTDOWN_MS = 8000;

	interface Props {
		title: string;
		mediaId?: string;
		onnext?: () => void;
		onclose: () => void;
	}

	let { title, mediaId, onnext, onclose }: Props = $props();

	onMount(() => {
		if (!onnext) return;
		const timer = setTimeout(() => onnext?.(), NEXT_COUNTDOWN_MS);
		return () => clearTimeout(timer);
	});
</script>

<div class="end-screen absolute inset-0 z-20 bg-black/70">
	<button
		class="glass-bar absolute top-[calc(var(--safe-top)+14px)] left-5 flex h-12 w-12 items-center justify-center rounded-full"
		aria-label="Close player"
		onclick={onclose}
	>
		<XIcon size={24} weight="bold" />
	</button>

	<div class="absolute bottom-[calc(var(--safe-bottom)+24px)] left-6 flex flex-col gap-3">
		<p class="text-2xl font-black tracking-tight">{title}</p>
		{#if mediaId}<RatingTrio {mediaId} />{/if}
	</div>

	{#if onnext}
		<div class="absolute right-6 bottom-[calc(var(--safe-bottom)+24px)] flex gap-3">
			<button class="glass-bar h-11 rounded-full px-5 text-sm font-semibold" onclick={onclose}>
				Watch Credits
			</button>
			<button
				class="next-button glass-bar relative flex h-11 items-center gap-2 overflow-hidden rounded-full px-5 text-sm font-semibold"
				style:--countdown="{NEXT_COUNTDOWN_MS}ms"
				onclick={onnext}
			>
				<span class="next-fill absolute inset-y-0 left-0 bg-white"></span>
				<span class="relative flex items-center gap-2 mix-blend-difference">
					<PlayIcon size={18} weight="fill" />
					Next Episode
				</span>
			</button>
		</div>
	{/if}
</div>

<style>
	.end-screen {
		animation: end-in 400ms ease-out;
	}

	@keyframes end-in {
		from {
			opacity: 0;
		}
	}

	/* Fills left to right over the countdown, like Netflix's autoplay bar. */
	.next-fill {
		width: 0;
		animation: next-fill var(--countdown) linear forwards;
	}

	@keyframes next-fill {
		to {
			width: 100%;
		}
	}
</style>
