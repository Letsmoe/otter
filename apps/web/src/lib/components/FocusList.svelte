<script lang="ts" module>
	// TV navigation tracks position in JS, not in DOM focus state. Exactly one
	// focus zone is "active" at a time (module-level token); arrow keys are
	// handled by a window listener in the active zone, so navigation keeps
	// working even when the browser drops element focus (DOM swaps, blur, …).
	//
	// The token is shared across FocusList and FocusGrid (via the exported
	// helpers) so the two coordinate: activating one deactivates the other.
	let activeListToken = $state<symbol | null>(null);

	export function activateFocusZone(token: symbol) {
		activeListToken = token;
	}

	export function isFocusZoneActive(token: symbol): boolean {
		return activeListToken === token;
	}
</script>

<script lang="ts" generics="T">
	// Reusable keyboard-navigable list — the single place arrow-key handling
	// lives, so pages and the sidebar don't each reinvent it.
	//
	// At a boundary it fires onedge(direction) instead of moving, letting the
	// caller hand focus to a neighbouring zone (e.g. content rail -> sidebar).
	import type { Snippet } from 'svelte';

	type Direction = 'left' | 'right' | 'up' | 'down';

	interface Props {
		items: T[];
		orientation?: 'horizontal' | 'vertical';
		item: Snippet<[T, number, boolean]>;
		onselect?: (item: T, index: number) => void;
		onedge?: (direction: Direction) => void;
		ariaLabel?: string;
		class?: string;
		// How the page scrolls when an item is focused. 'center' keeps the focused
		// element centered (default); 'top' snaps the page to the top instead, for
		// lists that live inside the hero banner.
		scroll?: 'center' | 'top';
	}

	let {
		items,
		orientation = 'horizontal',
		item,
		onselect,
		onedge,
		ariaLabel,
		class: className = '',
		scroll = 'center'
	}: Props = $props();

	const token = Symbol('focus-list');

	let focusedIndex = $state(0);
	const isActive = $derived(activeListToken === token);
	let nodes = $state<HTMLElement[]>([]);

	const prevKey = $derived(orientation === 'horizontal' ? 'ArrowLeft' : 'ArrowUp');
	const nextKey = $derived(orientation === 'horizontal' ? 'ArrowRight' : 'ArrowDown');
	const prevDir: Direction = $derived(orientation === 'horizontal' ? 'left' : 'up');
	const nextDir: Direction = $derived(orientation === 'horizontal' ? 'right' : 'down');
	// The cross-axis edge is still reported so a horizontal rail can escape left
	// into the sidebar via ArrowLeft while ArrowUp/Down move between rails.
	const crossPrev: Direction = $derived(orientation === 'horizontal' ? 'up' : 'left');
	const crossNext: Direction = $derived(orientation === 'horizontal' ? 'down' : 'right');

	function register(node: HTMLElement, index: number) {
		nodes[index] = node;
		return {
			destroy() {
				if (nodes[index] === node) nodes[index] = undefined as unknown as HTMLElement;
			}
		};
	}

	// Claim keyboard ownership for this list. DOM focus is a best-effort
	// side effect for accessibility — the JS state is the source of truth.
	function activate() {
		activeListToken = token;
	}

	function focusAt(index: number) {
		activate();
		focusedIndex = index;
		const node = nodes[index];
		if (!node) return;
		node.focus({ preventScroll: true });
		if (scroll === 'top') {
			window.scrollTo({ top: 0, behavior: 'smooth' });
			return;
		}
		node.scrollIntoView({ block: 'center', inline: 'center', behavior: 'smooth' });
	}

	/** Focus the current item — used by the coordinator to enter this list. */
	export function focus() {
		const index = Math.min(focusedIndex, items.length - 1);
		focusAt(Math.max(0, index));
	}

	/** Focus the first item — TV navigation enters a list at its start. */
	export function focusFirst() {
		if (items.length > 0) focusAt(0);
	}

	function move(delta: number, edge: Direction) {
		const next = focusedIndex + delta;
		if (next < 0 || next >= items.length) {
			onedge?.(edge);
			return;
		}
		focusAt(next);
	}

	function isTypingTarget(target: EventTarget | null): boolean {
		return target instanceof HTMLInputElement || target instanceof HTMLTextAreaElement;
	}

	// Window-level handler: only the active list responds, regardless of where
	// DOM focus currently is.
	function onWindowKeydown(event: KeyboardEvent) {
		if (!isActive) return;
		if (event.defaultPrevented || isTypingTarget(event.target)) return;

		if (event.key === nextKey) {
			event.preventDefault();
			move(1, nextDir);
		} else if (event.key === prevKey) {
			event.preventDefault();
			move(-1, prevDir);
		} else if (event.key === 'Enter' || event.key === ' ') {
			event.preventDefault();
			onselect?.(items[focusedIndex], focusedIndex);
		} else if (event.key === 'ArrowUp' && orientation === 'horizontal') {
			event.preventDefault();
			onedge?.(crossPrev);
		} else if (event.key === 'ArrowDown' && orientation === 'horizontal') {
			event.preventDefault();
			onedge?.(crossNext);
		} else if (event.key === 'ArrowLeft' && orientation === 'vertical') {
			event.preventDefault();
			onedge?.(crossPrev);
		} else if (event.key === 'ArrowRight' && orientation === 'vertical') {
			event.preventDefault();
			onedge?.(crossNext);
		}
	}

</script>

<svelte:window onkeydown={onWindowKeydown} />

<div
	class="focus-list {orientation} {className}"
	role="listbox"
	aria-label={ariaLabel}
	aria-orientation={orientation}
	tabindex="-1"
>
	{#each items as entry, index (index)}
		<div
			class="focus-item"
			class:focused={isActive && index === focusedIndex}
			role="option"
			aria-selected={isActive && index === focusedIndex}
			tabindex={index === focusedIndex ? 0 : -1}
			use:register={index}
			onclick={() => {
				focusAt(index);
				onselect?.(entry, index);
			}}
		>
			{@render item(entry, index, isActive && index === focusedIndex)}
		</div>
	{/each}
</div>

<style>
	.focus-list {
		display: flex;
		gap: var(--tv-gap, 1rem);
	}
	.focus-list.horizontal {
		flex-direction: row;
		overflow-x: auto;
		scroll-snap-type: x proximity;
		scrollbar-width: none;
		/* Breathing room so the focused card's scale-up isn't clipped by the
		   scroll container; the negative margin keeps the rail visually aligned. */
		padding: 0.75rem;
		margin: -0.75rem;
		scroll-padding-inline: 0.75rem;
	}
	.focus-list.horizontal::-webkit-scrollbar {
		display: none;
	}
	.focus-list.vertical {
		flex-direction: column;
	}
	.focus-item {
		scroll-snap-align: start;
		outline: none;
		border-radius: var(--tv-radius, 0.75rem);
	}
	/* Keep items at their intrinsic width so a horizontal rail overflow-scrolls
	   instead of squashing every card to fit. */
	.focus-list.horizontal > .focus-item {
		flex-shrink: 0;
	}
	/* Highlight driven by JS-tracked position, not DOM focus. */
	.focus-item.focused {
		outline: var(--tv-focus-ring, 2px solid #fafafa);
		outline-offset: 3px;
	}
</style>
