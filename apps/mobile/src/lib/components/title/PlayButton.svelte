<script lang="ts">
	// The title page's glass Play button: a light sweep while sources load,
	// one pop when the first source is ready.
	import PlayIcon from 'phosphor-svelte/lib/PlayIcon';

	interface Props {
		label: string;
		busy: boolean;
		ready: boolean;
		disabled: boolean;
		onclick: () => void;
	}

	let { label, busy, ready, disabled, onclick }: Props = $props();
</script>

<button
	class="play-button glass-bar relative flex h-12 items-center justify-center gap-2 overflow-hidden rounded-full font-semibold text-white disabled:opacity-60"
	class:play-button-busy={busy}
	class:play-button-ready={ready}
	{disabled}
	{onclick}
>
	<PlayIcon size={22} weight="fill" />
	{label}
</button>

<style>
	/* A light sweep crosses the button while sources load or a link resolves. */
	.play-button-busy::after {
		content: '';
		position: absolute;
		inset: 0;
		background: linear-gradient(
			100deg,
			transparent 30%,
			rgb(255 255 255 / 0.18) 50%,
			transparent 70%
		);
		translate: -100% 0;
		animation: play-sweep 1.4s ease-in-out infinite;
	}

	/* Pops once when the first source arrives. */
	.play-button-ready {
		animation: play-ready 520ms cubic-bezier(0.34, 1.56, 0.64, 1);
	}

	@keyframes play-sweep {
		to {
			translate: 100% 0;
		}
	}

	@keyframes play-ready {
		0% {
			scale: 0.94;
		}
		60% {
			scale: 1.03;
		}
		100% {
			scale: 1;
		}
	}

	@media (prefers-reduced-motion: reduce) {
		.play-button-busy::after,
		.play-button-ready {
			animation: none;
		}
	}
</style>
