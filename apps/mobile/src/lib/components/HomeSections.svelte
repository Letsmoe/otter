<script lang="ts">
	import { goto } from '$app/navigation';
	import Hero from './Hero.svelte';
	import MediaRail from './MediaRail.svelte';
	import { activeProfile } from '@neoworks-dev/otter-app/profile';
	import { sidebarFocus } from '$lib/stores/focus';
	import {
		fetchContinueWatching,
		fetchRecommendationRows,
		fetchTrending,
		detailPath,
		type RailItem,
		type RecommendationRail
	} from '@neoworks-dev/otter-app/discovery';

	const RAIL_SIZE = 25;

	interface Props {
		/** Off when the page shows its own featured preview above the rails. */
		showHero?: boolean;
	}

	let { showHero = true }: Props = $props();

	let hero = $state<RailItem | null>(null);
	let continueItems = $state<RailItem[]>([]);
	let recommendationRails = $state<RecommendationRail[]>([]);
	let trendingItems = $state<RailItem[]>([]);

	// The hero needs a backdrop to render, so prefer the first candidate that
	// carries one and only fall back to a banner-less item when none qualify.
	function pickHero(items: RailItem[]): RailItem | null {
		const withBackdrop = items.find((item) => item.backdropUrl);
		if (withBackdrop) return withBackdrop;
		if (items.length > 0) return items[0];
		return null;
	}

	function railKey(rail: RecommendationRail): string {
		return `recommendation-${rail.key}`;
	}

	// Refetch when the active profile changes. profileId is client-only
	// (localStorage), so this runs in the browser rather than an SSR loader.
	$effect(() => {
		const profile = $activeProfile;
		if (!profile) return;

		let cancelled = false;
		Promise.all([
			fetchContinueWatching(profile.id, RAIL_SIZE),
			fetchRecommendationRows(profile.id, 8, RAIL_SIZE)
		]).then(([continueResult, recommendationResult]) => {
			if (cancelled) return;
			continueItems = continueResult;
			recommendationRails = recommendationResult;
			const recommended = recommendationResult.flatMap((rail) => rail.items);
			hero = pickHero([...recommended, ...continueItems, ...trendingItems]);
		});
		return () => {
			cancelled = true;
		};
	});

	// Trending is profile-independent and keeps the page populated on a fresh install.
	$effect(() => {
		let cancelled = false;
		fetchTrending().then((items) => {
			if (cancelled) return;
			trendingItems = items;
			if (!hero) hero = pickHero(items);
		});
		return () => {
			cancelled = true;
		};
	});

	function openItem(item: RailItem) {
		goto(detailPath(item));
	}

	// Vertical coordination between the hero and the rails below it. Each section
	// registers a focus() ref; edges move to the previous/next present section.
	type SectionRef = { focus: () => void; focusFirst: () => void };
	let refs = $state<Record<string, SectionRef | undefined>>({});

	// Section the user last navigated to; focus returns here when the sidebar closes.
	let lastSectionKey = 'hero';

	function presentKeys(): string[] {
		const keys = ['hero'];
		if (continueItems.length > 0) keys.push('continue');
		for (const rail of recommendationRails) {
			if (rail.items.length > 0) keys.push(railKey(rail));
		}
		if (trendingItems.length > 0) keys.push('trending');
		return keys;
	}

	function move(fromKey: string, direction: 'up' | 'down') {
		const keys = presentKeys();
		const index = keys.indexOf(fromKey);
		let targetIndex = index - 1;
		if (direction === 'down') targetIndex = index + 1;
		const targetKey = keys[targetIndex];
		if (!targetKey) return;
		lastSectionKey = targetKey;
		refs[targetKey]?.focusFirst();
	}

	// Initial focus lands on the hero's Play button once the hero has rendered.
	let initialFocusDone = false;
	$effect(() => {
		if (hero === null || initialFocusDone) return;
		initialFocusDone = true;
		queueMicrotask(() => refs.hero?.focusFirst());
	});

	// When the sidebar drawer closes, hand focus back to the last active section.
	let sidebarWasOpen = false;
	$effect(() => {
		const open = $sidebarFocus.open;
		if (sidebarWasOpen && !open) {
			queueMicrotask(() => refs[lastSectionKey]?.focus());
		}
		sidebarWasOpen = open;
	});
</script>

{#snippet continueRail()}
	{#if continueItems.length > 0}
		<MediaRail
			bind:this={refs.continue}
			title="Continue watching"
			items={continueItems}
			variant="banner"
			scroll="top"
			onselect={openItem}
			onedge={(direction) => move('continue', direction)}
		/>
	{/if}
{/snippet}

<div class="flex flex-col gap-8">
	{#if showHero}
	<Hero
		bind:this={refs.hero}
		item={hero}
		onplay={openItem}
		oninfo={openItem}
		onedge={() => move('hero', 'down')}
	>
		{@render continueRail()}
	</Hero>
	{:else}
		{@render continueRail()}
	{/if}

	{#each recommendationRails as rail (rail.key)}
		{#if rail.items.length > 0}
			<MediaRail
				bind:this={refs[railKey(rail)]}
				title={rail.title}
				items={rail.items}
				onselect={openItem}
				onedge={(direction) => move(railKey(rail), direction)}
			/>
		{/if}
	{/each}

	{#if trendingItems.length > 0}
		<MediaRail
			bind:this={refs.trending}
			title="Trending now"
			items={trendingItems}
			onselect={openItem}
			onedge={(direction) => move('trending', direction)}
		/>
	{/if}
</div>
