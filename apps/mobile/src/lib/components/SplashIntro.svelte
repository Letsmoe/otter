<script lang="ts">
	// App start intro in the spirit of Netflix's: the OTTER letters rise one by
	// one, a light sweeps across them, thin light streaks burst out and the
	// wordmark zooms through the screen into the app. Tap to skip.
	import { onMount } from 'svelte';

	const LETTERS = ['O', 'T', 'T', 'E', 'R'];
	const INTRO_MS = 2300;
	// Streak positions (percent of the wordmark width) and their delays.
	const STREAKS = [
		{ left: 6, delay: 0 },
		{ left: 22, delay: 60 },
		{ left: 38, delay: 20 },
		{ left: 51, delay: 90 },
		{ left: 64, delay: 40 },
		{ left: 79, delay: 110 },
		{ left: 93, delay: 70 }
	];

	interface Props {
		ondone: () => void;
	}

	let { ondone }: Props = $props();

	onMount(() => {
		if (matchMedia('(prefers-reduced-motion: reduce)').matches) {
			ondone();
			return;
		}
		const timer = setTimeout(ondone, INTRO_MS);
		return () => clearTimeout(timer);
	});
</script>

<button
	data-backdrop
	class="intro fixed inset-0 z-[80] flex cursor-default items-center justify-center bg-black"
	aria-label="Skip intro"
	onclick={ondone}
>
	<span class="wordmark relative flex">
		{#each LETTERS as letter, index (index)}
			<span class="letter" style:--index={index}>{letter}</span>
		{/each}
		<span class="sweep" aria-hidden="true"></span>
		{#each STREAKS as streak, index (index)}
			<span
				class="streak"
				style:left="{streak.left}%"
				style:--delay="{streak.delay}ms"
				aria-hidden="true"
			></span>
		{/each}
	</span>
</button>

<style>
	.intro {
		animation: intro-out 2300ms linear forwards;
	}

	.wordmark {
		font-size: clamp(3rem, 16vw, 6rem);
		font-weight: 900;
		letter-spacing: 0.12em;
		line-height: 1;
		animation: wordmark-zoom 2300ms cubic-bezier(0.7, 0, 0.84, 0) forwards;
	}

	.letter {
		display: inline-block;
		color: white;
		clip-path: inset(100% 0 0 0);
		translate: 0 30%;
		animation: letter-rise 520ms cubic-bezier(0.22, 1, 0.36, 1) forwards;
		animation-delay: calc(120ms + var(--index) * 90ms);
		text-shadow: 0 0 24px rgb(255 255 255 / 0.25);
	}

	/* A soft light band crossing the letters once they stand. */
	.sweep {
		position: absolute;
		inset: -10% -20%;
		background: linear-gradient(
			100deg,
			transparent 35%,
			rgb(255 255 255 / 0.55) 50%,
			transparent 65%
		);
		mix-blend-mode: overlay;
		translate: -100% 0;
		animation: sweep 700ms ease-in-out 820ms forwards;
	}

	/* Thin streaks shooting up and down out of the wordmark as it zooms. */
	.streak {
		position: absolute;
		top: 50%;
		width: 2px;
		height: 0.9em;
		translate: -50% -50%;
		background: linear-gradient(to bottom, transparent, rgb(255 255 255 / 0.9), transparent);
		opacity: 0;
		animation: streak 700ms ease-out forwards;
		animation-delay: calc(1450ms + var(--delay));
	}

	@keyframes letter-rise {
		to {
			clip-path: inset(0 0 0 0);
			translate: 0 0;
		}
	}

	@keyframes sweep {
		to {
			translate: 100% 0;
		}
	}

	@keyframes streak {
		0% {
			opacity: 0;
			scale: 1 0.2;
		}
		30% {
			opacity: 1;
		}
		100% {
			opacity: 0;
			scale: 1 9;
		}
	}

	/* Holds, then rushes towards the viewer. */
	@keyframes wordmark-zoom {
		0%,
		62% {
			scale: 1;
			opacity: 1;
		}
		100% {
			scale: 16;
			opacity: 0;
		}
	}

	@keyframes intro-out {
		0%,
		80% {
			opacity: 1;
		}
		100% {
			opacity: 0;
		}
	}
</style>
