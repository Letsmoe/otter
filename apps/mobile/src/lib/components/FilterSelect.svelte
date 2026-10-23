<script lang="ts">
	// Glass pill showing "Label: choice" that opens a glass dropdown. The menu is
	// fixed-positioned so the pills' horizontal scroller doesn't clip it.
	import CaretDownIcon from 'phosphor-svelte/lib/CaretDownIcon';
	import CheckIcon from 'phosphor-svelte/lib/CheckIcon';

	const MENU_GAP = 6;
	const SCREEN_MARGIN = 12;
	const MENU_WIDTH = 200;

	interface Props {
		label: string;
		options: { value: string; label: string }[];
		selected: string;
		onselect: (value: string) => void;
		/** False shows only the choice, e.g. "Season 1". */
		showLabel?: boolean;
	}

	let { label, options, selected, onselect, showLabel = true }: Props = $props();

	let trigger = $state<HTMLButtonElement>();
	let open = $state(false);
	let menuTop = $state(0);
	let menuLeft = $state(0);

	let selectedLabel = $derived(options.find((option) => option.value === selected)?.label || '');
	// Filters dim while on their default; a plain chooser always reads as set.
	let isActive = $derived(!showLabel || selected !== options[0]?.value);

	function placeMenu() {
		if (!trigger) return;
		const rect = trigger.getBoundingClientRect();
		const maximumLeft = window.innerWidth - MENU_WIDTH - SCREEN_MARGIN;
		menuTop = rect.bottom + MENU_GAP;
		menuLeft = Math.max(SCREEN_MARGIN, Math.min(rect.left, maximumLeft));
	}

	function toggle() {
		placeMenu();
		open = !open;
	}

	function choose(value: string) {
		open = false;
		onselect(value);
	}
</script>

<button
	bind:this={trigger}
	class="glass-bar flex h-9 shrink-0 items-center gap-1.5 rounded-full px-4 text-[13px] transition-colors {isActive
		? 'text-base-content font-semibold'
		: 'text-base-content/60'}"
	aria-haspopup="listbox"
	aria-expanded={open}
	onclick={toggle}
>
	{#if showLabel}{label}:{/if}
	{selectedLabel}
	<CaretDownIcon size={14} class="transition-transform {open ? 'rotate-180' : ''}" />
</button>

{#if open}
	<button
		data-backdrop
		class="fixed inset-0 z-40 cursor-default"
		aria-label="Close"
		onclick={() => (open = false)}
	></button>
	<div
		role="listbox"
		aria-label={label}
		class="filter-menu glass-bar fixed z-50 flex flex-col rounded-2xl p-1"
		style:top="{menuTop}px"
		style:left="{menuLeft}px"
		style:width="{MENU_WIDTH}px"
	>
		{#each options as option (option.value)}
			<button
				role="option"
				aria-selected={option.value === selected}
				class="flex h-10 items-center justify-between rounded-xl px-3 text-left text-sm {option.value ===
				selected
					? 'bg-base-content/10 text-base-content font-semibold'
					: 'text-base-content/70'}"
				onclick={() => choose(option.value)}
			>
				{option.label}
				{#if option.value === selected}<CheckIcon size={16} />{/if}
			</button>
		{/each}
	</div>
{/if}

<style>
	.filter-menu {
		transform-origin: top left;
		animation: menu-in 220ms cubic-bezier(0.34, 1.3, 0.64, 1);
	}

	@keyframes menu-in {
		from {
			opacity: 0;
			scale: 0.92;
		}
		to {
			opacity: 1;
			scale: 1;
		}
	}

	@media (prefers-reduced-motion: reduce) {
		.filter-menu {
			animation: none;
		}
	}
</style>
