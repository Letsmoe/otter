<script lang="ts">
	// Floating glass panel over the player: on the right (speed, sources) or
	// centred and large (audio and subtitles), with a close button.
	import type { Snippet } from 'svelte';
	import { cubicIn } from 'svelte/easing';
	import XIcon from 'phosphor-svelte/lib/XIcon';

	interface Props {
		side?: boolean;
		onclose: () => void;
		children: Snippet;
	}

	let { side = false, onclose, children }: Props = $props();

	// Leaves the way it came: side panels slide out right, large ones shrink away.
	function leave(node: HTMLElement) {
		return {
			duration: 240,
			easing: cubicIn,
			css: (progress: number) => {
				if (side) return `translate: calc(${(1 - progress) * 100}% + ${(1 - progress) * 12}px) 0`;
				return `opacity: ${progress}; scale: ${0.94 + progress * 0.06}`;
			}
		};
	}
</script>

<button
	data-backdrop
	class="absolute inset-0 z-10 cursor-default"
	aria-label="Close panel"
	onclick={onclose}
></button>
<div
	out:leave|global
	class="sheet glass-bar absolute z-20 flex flex-col overflow-hidden rounded-3xl"
	class:sheet-side={side}
	class:sheet-full={!side}
	role="dialog"
>
	<button
		class="glass-bar absolute top-4 right-4 z-10 flex h-10 w-10 items-center justify-center rounded-full"
		aria-label="Close"
		onclick={onclose}
	>
		<XIcon size={22} />
	</button>
	{@render children()}
</div>

<style>
	.sheet-side {
		top: calc(var(--safe-top) + 12px);
		bottom: calc(var(--safe-bottom) + 12px);
		right: 12px;
		width: min(24rem, 50%);
		animation: sheet-in-side 340ms cubic-bezier(0.22, 1, 0.36, 1);
	}

	.sheet-full {
		inset: calc(var(--safe-top) + 16px) 32px calc(var(--safe-bottom) + 16px);
		animation: sheet-in-full 280ms cubic-bezier(0.34, 1.3, 0.64, 1);
	}

	@keyframes sheet-in-side {
		from {
			translate: calc(100% + 12px) 0;
		}
	}

	@keyframes sheet-in-full {
		from {
			opacity: 0;
			scale: 0.94;
		}
	}
</style>
