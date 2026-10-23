<script lang="ts">
	// Floating header: the logo, Watch Together and search buttons on a
	// fade to black, with optional content below such as a category switch.
	// Slides away when hidden.
	import type { Snippet } from 'svelte';
	import MagnifyingGlassIcon from 'phosphor-svelte/lib/MagnifyingGlassIcon';
	import TogetherButton from './together/TogetherButton.svelte';

	interface Props {
		hidden?: boolean;
		/** Height of the header including its gap to the screen top, for content padding. */
		height?: number;
		children?: Snippet;
	}

	let { hidden = false, height = $bindable(0), children }: Props = $props();
</script>

<div
	class="app-header fixed inset-x-0 top-0 z-30 bg-gradient-to-b from-black/90 via-black/70 to-transparent px-3 pt-[var(--safe-top)] pb-3"
	class:app-header-hidden={hidden}
	bind:clientHeight={height}
>
	<div class="flex flex-col gap-2">
		<div class="-mx-1 flex h-12 items-center justify-between">
			<span class="pl-3 text-lg font-black tracking-[0.18em]">OTTER</span>
			<div class="flex items-center">
				<TogetherButton />
				<a
					href="/search"
					class="flex h-10 w-10 items-center justify-center rounded-full"
					aria-label="Search"
				>
					<MagnifyingGlassIcon size={22} />
				</a>
			</div>
		</div>
		{#if children}
			{@render children()}
		{/if}
	</div>
</div>

<style>
	.app-header {
		transition:
			translate 320ms cubic-bezier(0.22, 1, 0.36, 1),
			opacity 220ms ease-out;
	}

	.app-header-hidden {
		translate: 0 -120%;
		opacity: 0;
		pointer-events: none;
	}

	@media (prefers-reduced-motion: reduce) {
		.app-header {
			transition: none;
		}
	}
</style>
