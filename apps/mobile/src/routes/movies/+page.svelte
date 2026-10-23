<script lang="ts">
	// Home: floating glass header (logo, search, categories) over scrolling content.
	import CategoryChips, { type Category } from '$lib/components/home/CategoryChips.svelte';
	import TitlesPage from '$lib/components/home/TitlesPage.svelte';
	import AppHeader from '$lib/components/AppHeader.svelte';

	// Scroll distance before the header reacts, so small jitters don't toggle it.
	const SCROLL_THRESHOLD = 8;
	const ALWAYS_SHOWN_ABOVE = 48;

	let category = $state<Category>('movies');
	let headerHidden = $state(false);
	let headerHeight = $state(0);
	let lastScrollTop = 0;

	// Hides the header while scrolling down, brings it back when scrolling up.
	function onContentScroll(event: Event) {
		const scrollTop = (event.currentTarget as HTMLElement).scrollTop;
		const delta = scrollTop - lastScrollTop;
		if (Math.abs(delta) < SCROLL_THRESHOLD) return;
		headerHidden = delta > 0 && scrollTop > ALWAYS_SHOWN_ABOVE;
		lastScrollTop = scrollTop;
	}
</script>

<div class="h-full">
	<AppHeader hidden={headerHidden} bind:height={headerHeight}>
		<CategoryChips selected={category} onselect={(chosen) => (category = chosen)} />
	</AppHeader>

	<div
		class="no-scrollbar pb-bottom-nav h-full overflow-y-auto overscroll-y-contain px-3"
		style:padding-top="{headerHeight + 12}px"
		onscroll={onContentScroll}
	>
		{#if category === 'movies'}
			<TitlesPage kind="movie" />
		{:else if category === 'tv'}
			<TitlesPage kind="series" />
		{/if}
	</div>
</div>
