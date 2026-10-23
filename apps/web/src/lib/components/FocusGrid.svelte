<script lang="ts" generics="T">
	// A keyboard-navigable 2D grid, the grid counterpart to FocusList. Column
	// count is read from the live layout so navigation stays correct across the
	// responsive breakpoints. Shares FocusList's active-zone token so only one
	// zone responds to arrow keys at a time.
	import type { Snippet } from 'svelte';
	import { activateFocusZone, isFocusZoneActive } from './FocusList.svelte';

	type Direction = 'left' | 'right' | 'up' | 'down';

	interface Props {
		items: T[];
		item: Snippet<[T, number, boolean]>;
		onselect?: (item: T, index: number) => void;
		// Fired when navigation leaves the grid at an outer edge.
		onedge?: (direction: Direction) => void;
		// Fired when focus reaches the last row, so the page can prefetch more.
		onnearend?: () => void;
		ariaLabel?: string;
		// Tailwind grid classes (columns/gap) applied to the grid container.
		gridClass?: string;
	}

	let { items, item, onselect, onedge, onnearend, ariaLabel, gridClass = '' }: Props = $props();

	const token = Symbol('focus-grid');
	let focusedIndex = $state(0);
	const isActive = $derived(isFocusZoneActive(token));
	let nodes = $state<HTMLElement[]>([]);

	function register(node: HTMLElement, index: number) {
		nodes[index] = node;
		return {
			destroy() {
				if (nodes[index] === node) nodes[index] = undefined as unknown as HTMLElement;
			}
		};
	}

	// Columns = how many leading cards share the first card's top offset.
	function columns(): number {
		if (nodes.length < 2 || !nodes[0]) return 1;
		const top = nodes[0].offsetTop;
		let count = 0;
		for (const node of nodes) {
			if (!node || node.offsetTop !== top) break;
			count++;
		}
		return Math.max(1, count);
	}

	function focusAt(index: number) {
		activateFocusZone(token);
		focusedIndex = index;
		const node = nodes[index];
		if (!node) return;
		node.focus({ preventScroll: true });
		node.scrollIntoView({ block: 'nearest', behavior: 'smooth' });
		if (index >= items.length - columns()) onnearend?.();
	}

	/** Focus the current item — used by a coordinator to enter this grid. */
	export function focus() {
		const index = Math.min(focusedIndex, items.length - 1);
		if (index >= 0) focusAt(index);
	}

	/** Focus the first item. */
	export function focusFirst() {
		if (items.length > 0) focusAt(0);
	}

	function isTypingTarget(target: EventTarget | null): boolean {
		return target instanceof HTMLInputElement || target instanceof HTMLTextAreaElement;
	}

	function onWindowKeydown(event: KeyboardEvent) {
		if (!isActive) return;
		if (event.defaultPrevented || isTypingTarget(event.target)) return;

		const cols = columns();
		const index = focusedIndex;
		const last = items.length - 1;

		if (event.key === 'ArrowRight') {
			event.preventDefault();
			if (index < last) focusAt(index + 1);
			else onedge?.('right');
		} else if (event.key === 'ArrowLeft') {
			event.preventDefault();
			if (index % cols === 0) onedge?.('left');
			else focusAt(index - 1);
		} else if (event.key === 'ArrowUp') {
			event.preventDefault();
			if (index < cols) onedge?.('up');
			else focusAt(index - cols);
		} else if (event.key === 'ArrowDown') {
			event.preventDefault();
			if (index + cols <= last) focusAt(index + cols);
			else if (index === last) onedge?.('down');
			else focusAt(last);
		} else if (event.key === 'Enter' || event.key === ' ') {
			event.preventDefault();
			onselect?.(items[index], index);
		}
	}
</script>

<svelte:window onkeydown={onWindowKeydown} />

<div class="{gridClass} grid" role="grid" aria-label={ariaLabel}>
	{#each items as entry, index (index)}
		<div
			class="focus-item"
			class:focused={isActive && index === focusedIndex}
			role="gridcell"
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
	.focus-item {
		outline: none;
		border-radius: var(--tv-radius, 0.75rem);
	}
	.focus-item.focused {
		outline: var(--tv-focus-ring, 2px solid #fafafa);
		outline-offset: 3px;
	}
</style>
