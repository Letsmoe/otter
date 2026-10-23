<script lang="ts">
	// Large poster card at the top of a home page: title art, genres, Play and My List.
	import { goto } from '$app/navigation';
	import { activeProfile } from '@neoworks-dev/otter-app/profile';
	import {
		addToWatchlist,
		detailPath,
		fetchFeatured,
		fetchInWatchlist,
		removeFromWatchlist,
		type FeaturedItem,
		type MediaKind
	} from '@neoworks-dev/otter-app/discovery';
	import PlayIcon from 'phosphor-svelte/lib/PlayIcon';
	import PlusIcon from 'phosphor-svelte/lib/PlusIcon';
	import CheckIcon from 'phosphor-svelte/lib/CheckIcon';

	const MAX_GENRES = 3;

	interface Props {
		kind: MediaKind;
		/** Reports the featured title so the rails below can skip it. */
		onfeatured?: (id: string) => void;
	}

	let { kind, onfeatured }: Props = $props();

	let featured = $state<FeaturedItem | null>(null);
	let inList = $state(false);

	$effect(() => {
		const profile = $activeProfile;
		if (!profile) return;
		let cancelled = false;
		fetchFeatured(profile.id, kind).then(async (item) => {
			if (cancelled || !item) return;
			featured = item;
			onfeatured?.(item.id);
			inList = await fetchInWatchlist(profile.id, item.id);
		});
		return () => {
			cancelled = true;
		};
	});

	async function toggleList() {
		const profile = $activeProfile;
		if (!profile || !featured) return;
		if (inList) await removeFromWatchlist(profile.id, featured.id);
		else await addToWatchlist(profile.id, featured.id);
		inList = !inList;
	}

	function open() {
		if (featured) goto(detailPath(featured));
	}
</script>

{#if featured}
	<div class="relative mx-auto aspect-[2/3] w-full max-w-md overflow-hidden rounded-lg shadow-2xl ring-1 ring-white/15">
		<button class="absolute inset-0" aria-label={featured.title} onclick={open}>
			<img src={featured.posterUrl} alt="" class="h-full w-full object-cover" />
		</button>
		<div
			class="pointer-events-none absolute inset-x-0 bottom-0 h-2/5 bg-gradient-to-t from-black via-black/70 to-transparent"
		></div>
		<div class="absolute inset-x-0 bottom-0 flex flex-col items-center gap-3 p-4">
			<p class="text-center text-sm text-white/85">
				{featured.genres.slice(0, MAX_GENRES).join(' • ')}
			</p>
			<div class="flex w-full gap-3">
				<button
					class="flex h-11 flex-1 items-center justify-center gap-2 rounded-md bg-white font-semibold text-black"
					onclick={open}
				>
					<PlayIcon size={22} weight="fill" />
					Play
				</button>
				<button
					class="flex h-11 flex-1 items-center justify-center gap-2 rounded-md bg-white/25 font-semibold text-white backdrop-blur"
					onclick={toggleList}
				>
					{#if inList}<CheckIcon size={22} />{:else}<PlusIcon size={22} />{/if}
					My List
				</button>
			</div>
		</div>
	</div>
{:else}
	<div class="mx-auto aspect-[2/3] w-full max-w-md animate-pulse rounded-lg bg-white/5"></div>
{/if}
