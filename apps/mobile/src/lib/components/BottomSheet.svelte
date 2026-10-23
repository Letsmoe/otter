<script lang="ts">
	// Glass sheet sliding up from the bottom over a dimmed page. Dragging it
	// down (from the handle, or from content scrolled to the top) closes it.
	import type { Snippet } from 'svelte';
	import { fade } from 'svelte/transition';
	import { cubicIn, cubicOut } from 'svelte/easing';

	// Movement before a touch counts as a drag.
	const SLOP_PIXELS = 8;
	// Released past this distance (px) or faster than this (px/ms), it closes.
	const CLOSE_DISTANCE = 120;
	const CLOSE_VELOCITY = 0.5;
	const NO_DRAG = 'input, textarea, [contenteditable]';

	interface Props {
		onclose: () => void;
		children: Snippet;
	}

	let { onclose, children }: Props = $props();

	let sheet = $state<HTMLDivElement>();
	let dragOffset = $state(0);
	let dragging = $state(false);
	let tracking = false;
	let startY = 0;
	let lastY = 0;
	let lastTime = 0;
	let velocity = 0;

	// Slides the sheet by its own height: up on open, back down on close. A
	// sheet closed by dragging continues from where the finger left it.
	function slide(
		node: HTMLElement,
		{ duration, easing }: { duration: number; easing: (t: number) => number }
	) {
		const startOffset = dragOffset;
		return {
			duration,
			easing,
			css: (progress: number) =>
				`translate: 0 calc(${startOffset * progress}px + ${(1 - progress) * 100}%)`
		};
	}

	// Content that can still scroll up keeps the gesture for itself.
	function canStartDrag(target: EventTarget | null): boolean {
		if (!(target instanceof Element) || target.closest(NO_DRAG)) return false;
		for (let element: Element | null = target; element; element = element.parentElement) {
			if (element.scrollTop > 0) return false;
			if (element === sheet) return true;
		}
		return true;
	}

	function onTouchStart(event: TouchEvent) {
		tracking = canStartDrag(event.target);
		startY = event.touches[0].clientY;
		lastY = startY;
		lastTime = event.timeStamp;
		velocity = 0;
	}

	function onTouchMove(event: TouchEvent) {
		if (!tracking) return;
		const y = event.touches[0].clientY;
		const distance = y - startY;
		if (!dragging && !startDrag(distance)) return;
		event.preventDefault();
		velocity = (y - lastY) / Math.max(1, event.timeStamp - lastTime);
		lastY = y;
		lastTime = event.timeStamp;
		dragOffset = Math.max(0, distance - SLOP_PIXELS);
	}

	// Moving down past the slop starts a drag; moving up hands over to scrolling.
	function startDrag(distance: number): boolean {
		if (distance < -SLOP_PIXELS) tracking = false;
		if (distance <= SLOP_PIXELS) return false;
		dragging = true;
		return true;
	}

	function onTouchEnd() {
		tracking = false;
		if (!dragging) return;
		dragging = false;
		if (dragOffset > CLOSE_DISTANCE || velocity > CLOSE_VELOCITY) {
			onclose();
			return;
		}
		dragOffset = 0;
	}

	// touchmove must be non-passive to stop the content scrolling while dragging.
	$effect(() => {
		const element = sheet;
		if (!element) return;
		element.addEventListener('touchmove', onTouchMove, { passive: false });
		return () => element.removeEventListener('touchmove', onTouchMove);
	});
</script>

<div class="fixed inset-0 z-[60] flex flex-col justify-end" role="dialog" aria-modal="true">
	<button
		data-backdrop
		class="absolute inset-0 cursor-default bg-black/60"
		aria-label="Close"
		onclick={onclose}
		in:fade|global={{ duration: 200 }}
		out:fade|global={{ duration: 260 }}
	></button>
	<!-- svelte-ignore a11y_no_static_element_interactions -->
	<div
		bind:this={sheet}
		in:slide|global={{ duration: 380, easing: cubicOut }}
		out:slide|global={{ duration: 260, easing: cubicIn }}
		class="glass-bar relative flex max-h-[85dvh] flex-col overflow-y-auto rounded-t-[2rem] px-5 pt-3 pb-[calc(var(--safe-bottom)+20px)]"
		class:sheet-settling={!dragging}
		style:translate="0 {dragOffset}px"
		ontouchstart={onTouchStart}
		ontouchend={onTouchEnd}
		ontouchcancel={onTouchEnd}
	>
		<span class="mx-auto mb-3 h-1 w-10 shrink-0 rounded-full bg-white/30"></span>
		{@render children()}
	</div>
</div>

<style>
	.sheet-settling {
		transition: translate 320ms cubic-bezier(0.22, 1, 0.36, 1);
	}
</style>
