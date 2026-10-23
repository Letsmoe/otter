<script lang="ts">
	// One Home category (movies or TV shows): featured preview, then rails.
	import { activeProfile } from '@neoworks-dev/otter-app/profile';
	import {
		fetchContinueWatching,
		fetchGenreRails,
		fetchMoreWith,
		fetchNewAndComingSoon,
		fetchRecommended,
		fetchTrending,
		fetchWatchAgain,
		fetchWatchlist,
		onlyKind,
		withoutRepeats,
		type GenreRail,
		type MediaKind,
		type MoreWithRail,
		type RailItem
	} from '@neoworks-dev/otter-app/discovery';
	import FeaturedPreview from './FeaturedPreview.svelte';
	import PosterRail from './PosterRail.svelte';

	const LABELS: Record<MediaKind, { plural: string; newRow: string }> = {
		movie: { plural: 'Movies', newRow: 'New & Coming Soon' },
		series: { plural: 'Shows', newRow: 'New Episodes' }
	};

	interface Props {
		kind: MediaKind;
	}

	let { kind }: Props = $props();

	let labels = $derived(LABELS[kind]);

	let continueWatching = $state<RailItem[]>([]);
	let myList = $state<RailItem[]>([]);
	let newAndComingSoon = $state<RailItem[]>([]);
	let recommended = $state<RailItem[]>([]);
	let trending = $state<RailItem[]>([]);
	let moreWith = $state<MoreWithRail | null>(null);
	let genreRails = $state<GenreRail[]>([]);
	let watchAgain = $state<RailItem[]>([]);

	// Each row loads on its own so fast rows don't wait for slow ones.
	$effect(() => {
		const profile = $activeProfile;
		if (!profile) return;
		let cancelled = false;
		function load<T>(request: Promise<T>, apply: (value: T) => void) {
			request.then((value) => {
				if (!cancelled) apply(value);
			});
		}
		load(fetchContinueWatching(profile.id), (items) => (continueWatching = onlyKind(items, kind)));
		load(fetchWatchlist(profile.id), (items) => (myList = onlyKind(items, kind)));
		load(fetchRecommended(profile.id, kind), (items) => (recommended = items));
		load(fetchMoreWith(profile.id, kind), (row) => (moreWith = row));
		load(fetchGenreRails(profile.id, kind), (rails) => (genreRails = rails));
		load(fetchWatchAgain(profile.id, kind), (items) => (watchAgain = items));
		return () => {
			cancelled = true;
		};
	});

	$effect(() => {
		fetchNewAndComingSoon(kind).then((items) => (newAndComingSoon = items));
		fetchTrending(kind).then((items) => (trending = items));
	});

	let featuredId = $state<string | null>(null);

	// Rails in page order; a title only shows in the first rail that has it.
	let shown = $derived.by(() => {
		const featured: string[] = [];
		if (featuredId) featured.push(featuredId);
		const moreWithItems: RailItem[] = [];
		if (moreWith) moreWithItems.push(...moreWith.items);
		const [continueItems, listItems, newItems, recommendedItems, trendingItems, moreItems, ...rest] =
			withoutRepeats(
				[
					continueWatching,
					myList,
					newAndComingSoon,
					recommended,
					trending,
					moreWithItems,
					...genreRails.map((rail) => rail.items),
					watchAgain
				],
				featured
			);
		return {
			continueItems,
			listItems,
			newItems,
			recommendedItems,
			trendingItems,
			moreItems,
			genreItems: rest.slice(0, genreRails.length),
			watchAgainItems: rest[genreRails.length]
		};
	});

	function continueTitle(): string {
		if (!$activeProfile) return 'Continue Watching';
		return `Continue Watching for ${$activeProfile.name}`;
	}
</script>

<div class="flex flex-col gap-6">
	<FeaturedPreview {kind} onfeatured={(id) => (featuredId = id)} />
	<PosterRail title={continueTitle()} items={shown.continueItems} />
	<PosterRail title="My List" items={shown.listItems} />
	<PosterRail title={labels.newRow} items={shown.newItems} />
	<PosterRail title="Recommended {labels.plural}" items={shown.recommendedItems} size="large" />
	<PosterRail title="Trending {labels.plural}" items={shown.trendingItems} size="large" />
	{#if moreWith}
		<PosterRail title="More with {moreWith.personName}" items={shown.moreItems} />
	{/if}
	{#each genreRails as rail, index (rail.genre)}
		<PosterRail title="{rail.genre} {labels.plural}" items={shown.genreItems[index]} />
	{/each}
	<PosterRail title="Watch It Again" items={shown.watchAgainItems} />
</div>
