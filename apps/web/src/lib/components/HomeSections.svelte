<script lang="ts">
	import { goto } from '$app/navigation';
	import Hero from './Hero.svelte';
	import MediaRail from './MediaRail.svelte';
	import { activeProfile } from '@letsmoe/otter-app/profile';
	import { sidebarFocus } from '$lib/stores/focus';
	import { notifications } from '$lib/stores/notifications';
	import {
		dismissRecommendation,
		fetchContinueWatching,
		fetchRecommendationRows,
		fetchTrending,
		restoreRecommendation,
		detailPath,
		type RailItem,
		type RecommendationRail
	} from '@letsmoe/otter-app/discovery';

	const RAIL_SIZE = 25;

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

	function withoutItem(rails: RecommendationRail[], itemId: string): RecommendationRail[] {
		return rails.map((rail) => ({
			...rail,
			items: rail.items.filter((entry) => entry.id !== itemId)
		}));
	}

	async function reloadRecommendations(profileId: string) {
		recommendationRails = await fetchRecommendationRows(profileId, 8, RAIL_SIZE);
	}

	async function undoDismiss(profileId: string, item: RailItem) {
		try {
			await restoreRecommendation(profileId, item.id);
		} catch {
			notifications.push(`Could not bring back ${item.title}`, 'error');
			return;
		}
		await reloadRecommendations(profileId);
	}

	// "Not for me": the card goes right away, the backend learns from it.
	async function dismissItem(item: RailItem) {
		const profile = $activeProfile;
		if (!profile) return;
		recommendationRails = withoutItem(recommendationRails, item.id);
		if (hero?.id === item.id) {
			const remaining = recommendationRails.flatMap((rail) => rail.items);
			hero = pickHero([...remaining, ...continueItems, ...trendingItems]);
		}
		try {
			await dismissRecommendation(profile.id, item.id);
		} catch {
			notifications.push(`Could not hide ${item.title}`, 'error');
			await reloadRecommendations(profile.id);
			return;
		}
		notifications.push(`${item.title} won't be recommended again`, 'info', {
			label: 'Undo',
			run: () => undoDismiss(profile.id, item)
		});
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

<div class="flex flex-col gap-8">
	<Hero
		bind:this={refs.hero}
		item={hero}
		onplay={openItem}
		oninfo={openItem}
		onedge={() => move('hero', 'down')}
	>
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
	</Hero>

	{#each recommendationRails as rail (rail.key)}
		{#if rail.items.length > 0}
			<MediaRail
				bind:this={refs[railKey(rail)]}
				title={rail.title}
				items={rail.items}
				onselect={openItem}
				ondismiss={dismissItem}
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
