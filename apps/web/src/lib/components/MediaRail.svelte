<script lang="ts">
	import { goto } from '$app/navigation';
	import ArrowRightIcon from 'phosphor-svelte/lib/ArrowRightIcon';
	import FocusList from './FocusList.svelte';
	import { sidebarFocus } from '$lib/stores/focus';
	import type { RailItem } from '@neoworks-dev/otter-app/discovery';

	// A leading "see all" link card that navigates to the full browse grid.
	type SeeAllEntry = { seeAllHref: string; seeAllLabel: string };
	type RailEntry = RailItem | SeeAllEntry;

	interface Props {
		title: string;
		items: RailItem[];
		onselect: (item: RailItem) => void;
		onedge?: (direction: 'up' | 'down') => void;
		// 'banner' shows landscape backdrops with a play-progress bar (continue
		// watching); 'poster' is the default portrait card.
		variant?: 'poster' | 'banner';
		// Page-scroll behaviour on focus. Rails nested inside the hero pass 'top'
		// so focusing a card keeps the hero framed instead of centering the card.
		scroll?: 'center' | 'top';
		// When set, a leading card links to the full browse grid.
		seeAll?: { href: string; label: string };
	}

	let {
		title,
		items,
		onselect,
		onedge,
		variant = 'poster',
		scroll = 'center',
		seeAll
	}: Props = $props();

	// Rails stay scannable: overflow-scroll horizontally, never more than 25 cards.
	const MAX_RAIL_ITEMS = 25;
	let entries = $derived<RailEntry[]>(
		seeAll
			? [{ seeAllHref: seeAll.href, seeAllLabel: seeAll.label }, ...items.slice(0, MAX_RAIL_ITEMS)]
			: items.slice(0, MAX_RAIL_ITEMS)
	);

	function isSeeAll(entry: RailEntry): entry is SeeAllEntry {
		return 'seeAllHref' in entry;
	}

	function selectEntry(entry: RailEntry) {
		if (isSeeAll(entry)) {
			goto(entry.seeAllHref);
			return;
		}
		onselect(entry);
	}

	let list = $state<{ focus: () => void; focusFirst: () => void }>();

	/** Move focus into this rail (called by the page's inter-rail coordinator). */
	export function focus() {
		list?.focus();
	}

	/** Enter this rail at its first card (vertical TV navigation). */
	export function focusFirst() {
		list?.focusFirst();
	}

	function handleEdge(direction: 'left' | 'right' | 'up' | 'down') {
		if (direction === 'left') {
			// Left edge of a content rail hands focus to the sidebar.
			sidebarFocus.openSidebar();
			return;
		}
		if (direction === 'up' || direction === 'down') {
			onedge?.(direction);
		}
	}
</script>

{#if items.length > 0}
	<section class="flex flex-col gap-3">
		<h2 class="text-base-content text-lg font-semibold">{title}</h2>
		<FocusList
			bind:this={list}
			items={entries}
			orientation="horizontal"
			ariaLabel={title}
			{scroll}
			onselect={(entry) => selectEntry(entry)}
			onedge={handleEdge}
		>
			{#snippet item(entry)}
				{#if isSeeAll(entry)}
					<div class="flex w-48 shrink-0 flex-col gap-2">
						<div
							class="tv-card bg-base-200 flex aspect-[2/3] w-48 flex-col items-center justify-center gap-3"
						>
							<span
								class="flex h-12 w-12 items-center justify-center rounded-full bg-white/10 text-white"
							>
								<ArrowRightIcon size={24} weight="bold" />
							</span>
						</div>
						<p class="text-base-content line-clamp-1 text-sm font-medium">{entry.seeAllLabel}</p>
					</div>
				{:else if variant === 'banner'}
					<div class="flex w-72 shrink-0 flex-col gap-2">
						<div class="tv-card relative aspect-video w-72">
							{#if entry.backdropUrl}
								<img
									src={entry.backdropUrl}
									alt={entry.title}
									loading="lazy"
									class="h-full w-full object-cover"
								/>
							{:else}
								<div class="flex h-full w-full items-center justify-center">
									<span class="text-5xl opacity-10">🎬</span>
								</div>
							{/if}
							{#if entry.progress !== undefined}
								<div class="absolute inset-x-0 bottom-0 h-1 bg-black/50">
									<div class="bg-primary h-full" style="width: {entry.progress * 100}%"></div>
								</div>
							{/if}
						</div>
						<p class="text-base-content line-clamp-1 text-sm font-medium">{entry.title}</p>
						{#if entry.subtitle}
							<p class="text-base-content/40 -mt-1 line-clamp-1 text-xs">{entry.subtitle}</p>
						{/if}
					</div>
				{:else}
					<div class="flex w-48 shrink-0 flex-col gap-2">
						<div class="tv-card relative aspect-[2/3] w-48">
							{#if entry.posterUrl}
								<img
									src={entry.posterUrl}
									alt={entry.title}
									loading="lazy"
									class="h-full w-full object-cover"
								/>
							{:else}
								<div class="flex h-full w-full items-center justify-center">
									<span class="text-5xl opacity-10">🎬</span>
								</div>
							{/if}
						</div>
						<p class="text-base-content line-clamp-1 text-sm font-medium">{entry.title}</p>
					</div>
				{/if}
			{/snippet}
		</FocusList>
	</section>
{/if}
