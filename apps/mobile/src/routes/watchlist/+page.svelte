<script lang="ts">
	// My List: poster grid with a type switch (All, Movies, TV Shows), a sort
	// choice and an edit mode to remove titles. Reloads whenever the tab is shown.
	import { page } from '$app/state';
	import PencilSimpleIcon from 'phosphor-svelte/lib/PencilSimpleIcon';
	import XIcon from 'phosphor-svelte/lib/XIcon';
	import BookmarkSimpleIcon from 'phosphor-svelte/lib/BookmarkSimpleIcon';
	import { activeProfile } from '@neoworks-dev/otter-app/profile';
	import {
		detailPath,
		fetchWatchlist,
		onlyKind,
		removeFromWatchlist,
		sortRailItems,
		type ListOrder,
		type MediaKind,
		type RailItem
	} from '@neoworks-dev/otter-app/discovery';
	import SegmentedBar from '$lib/components/SegmentedBar.svelte';
	import FilterSelect from '$lib/components/FilterSelect.svelte';

	type ListType = 'all' | MediaKind;

	const TYPE_OPTIONS: { value: ListType; label: string }[] = [
		{ value: 'all', label: 'All' },
		{ value: 'movie', label: 'Movies' },
		{ value: 'series', label: 'TV Shows' }
	];

	const ORDER_OPTIONS: { value: ListOrder; label: string }[] = [
		{ value: 'added', label: 'Recently Added' },
		{ value: 'title', label: 'A–Z' },
		{ value: 'release', label: 'Release Date' }
	];

	let items = $state<RailItem[]>([]);
	let loaded = $state(false);
	let listType = $state<ListType>('all');
	let order = $state<ListOrder>('added');
	let editing = $state(false);

	let shownItems = $derived(sortRailItems(itemsOfType(items, listType), order));

	function itemsOfType(all: RailItem[], type: ListType): RailItem[] {
		if (type === 'all') return all;
		return onlyKind(all, type);
	}

	// The pager keeps this pane mounted, so it reloads each time the tab opens.
	$effect(() => {
		const profile = $activeProfile;
		const shown = page.url.pathname === '/watchlist';
		if (!profile || !shown) return;
		let cancelled = false;
		fetchWatchlist(profile.id).then((result) => {
			if (cancelled) return;
			items = result;
			loaded = true;
		});
		return () => {
			cancelled = true;
		};
	});

	async function remove(item: RailItem) {
		const profile = $activeProfile;
		if (!profile) return;
		items = items.filter((candidate) => candidate.id !== item.id);
		await removeFromWatchlist(profile.id, item.id);
	}

	function emptyText(): string {
		if (listType === 'movie') return 'No movies on your list yet.';
		if (listType === 'series') return 'No shows on your list yet.';
		return 'Titles you add with + My List show up here.';
	}
</script>

<div class="flex flex-col gap-3 pt-3">
	<SegmentedBar
		options={TYPE_OPTIONS}
		selected={listType}
		onselect={(chosen) => (listType = chosen)}
	/>

	<div class="flex items-center justify-between">
		<FilterSelect
			label="Sort"
			options={ORDER_OPTIONS}
			selected={order}
			onselect={(value) => (order = value as ListOrder)}
		/>
		{#if items.length > 0}
			<button
				class="flex h-10 w-10 items-center justify-center rounded-full"
				class:glass-bar={editing}
				aria-label="Edit My List"
				aria-pressed={editing}
				onclick={() => (editing = !editing)}
			>
				<PencilSimpleIcon size={22} />
			</button>
		{/if}
	</div>

	{#if shownItems.length > 0}
		<div class="grid grid-cols-3 gap-2 pb-4">
			{#each shownItems as item (item.id)}
				<div class="relative">
					<a
						href={detailPath(item)}
						class="block aspect-[2/3] overflow-hidden rounded-md bg-white/5"
					>
						{#if item.posterUrl}
							<img
								src={item.posterUrl}
								alt={item.title}
								loading="lazy"
								class="h-full w-full object-cover"
							/>
						{:else}
							<span class="flex h-full items-center justify-center p-2 text-center text-xs"
								>{item.title}</span
							>
						{/if}
					</a>
					{#if editing}
						<button
							class="remove-button glass-bar absolute top-1.5 right-1.5 flex h-8 w-8 items-center justify-center rounded-full"
							aria-label="Remove {item.title} from My List"
							onclick={() => remove(item)}
						>
							<XIcon size={14} weight="bold" />
						</button>
					{/if}
				</div>
			{/each}
		</div>
	{:else if loaded}
		<div class="flex flex-col items-center gap-3 pt-16 text-center">
			<BookmarkSimpleIcon size={56} class="opacity-40" />
			<p class="max-w-xs text-sm opacity-60">{emptyText()}</p>
		</div>
	{/if}
</div>

<style>
	.remove-button {
		animation: remove-in 220ms cubic-bezier(0.34, 1.56, 0.64, 1);
	}

	@keyframes remove-in {
		from {
			scale: 0.4;
			opacity: 0;
		}
	}
</style>
