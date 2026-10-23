<script lang="ts">
	import { shareContent } from '$lib/share';
	// Netflix's icon row under the description: My List, Rate, Share and Download.
	import { activeProfile } from '@neoworks-dev/otter-app/profile';
	import {
		addToWatchlist,
		clearUserRating,
		fetchInWatchlist,
		fetchUserRating,
		removeFromWatchlist,
		setUserRating
	} from '@neoworks-dev/otter-app/discovery';
	import PlusIcon from 'phosphor-svelte/lib/PlusIcon';
	import CheckIcon from 'phosphor-svelte/lib/CheckIcon';
	import ThumbsUpIcon from 'phosphor-svelte/lib/ThumbsUpIcon';
	import ThumbsDownIcon from 'phosphor-svelte/lib/ThumbsDownIcon';
	import HeartIcon from 'phosphor-svelte/lib/HeartIcon';
	import ShareNetworkIcon from 'phosphor-svelte/lib/ShareNetworkIcon';
	import DownloadButton from '$lib/components/downloads/DownloadButton.svelte';
	import type { Component } from 'svelte';

	// Netflix's three ratings on the backend's 1-10 scale.
	const RATINGS: {
		label: string;
		value: number;
		icon: Component<{ size?: number; weight?: 'regular' | 'fill' }>;
	}[] = [
		{ label: 'Not for me', value: 2, icon: ThumbsDownIcon },
		{ label: 'I like this', value: 7.5, icon: ThumbsUpIcon },
		{ label: 'Love this!', value: 10, icon: HeartIcon }
	];

	interface Props {
		mediaId: string;
		title: string;
		shareUrl: string;
		/** Movie or episode to offer a download for; no button when unset. */
		downloadMediaId?: string;
	}

	let { mediaId, title, shareUrl, downloadMediaId }: Props = $props();

	let inList = $state(false);
	let rating = $state<number | null>(null);
	let rateOpen = $state(false);

	let currentRating = $derived(RATINGS.find((option) => option.value === rating));

	$effect(() => {
		const profile = $activeProfile;
		if (!profile) return;
		let cancelled = false;
		fetchInWatchlist(profile.id, mediaId).then((value) => {
			if (!cancelled) inList = value;
		});
		fetchUserRating(profile.id, mediaId).then((value) => {
			if (!cancelled) rating = value;
		});
		return () => {
			cancelled = true;
		};
	});

	async function toggleList() {
		const profile = $activeProfile;
		if (!profile) return;
		if (inList) await removeFromWatchlist(profile.id, mediaId);
		else await addToWatchlist(profile.id, mediaId);
		inList = !inList;
	}

	// Choosing the current rating again clears it.
	async function rate(value: number) {
		const profile = $activeProfile;
		rateOpen = false;
		if (!profile) return;
		if (rating === value) {
			await clearUserRating(profile.id, mediaId);
			rating = null;
			return;
		}
		await setUserRating(profile.id, mediaId, value);
		rating = value;
	}

	async function share() {
		await shareContent({ title, url: shareUrl });
	}
</script>

<div class="relative flex justify-around pt-2">
	<button
		class="flex w-20 flex-col items-center gap-1.5 text-[11px] text-white/70"
		onclick={toggleList}
	>
		{#if inList}<CheckIcon size={26} />{:else}<PlusIcon size={26} />{/if}
		My List
	</button>
	<button
		class="flex w-20 flex-col items-center gap-1.5 text-[11px] text-white/70"
		onclick={() => (rateOpen = !rateOpen)}
	>
		{#if currentRating}
			<currentRating.icon size={26} weight="fill" />
		{:else}
			<ThumbsUpIcon size={26} />
		{/if}
		Rate
	</button>
	<button class="flex w-20 flex-col items-center gap-1.5 text-[11px] text-white/70" onclick={share}>
		<ShareNetworkIcon size={26} />
		Share
	</button>
	{#if downloadMediaId}
		<DownloadButton mediaId={downloadMediaId} {title} />
	{/if}

	{#if rateOpen}
		<button
			data-backdrop
			class="fixed inset-0 z-40 cursor-default"
			aria-label="Close"
			onclick={() => (rateOpen = false)}
		></button>
		<div
			class="rate-menu glass-bar absolute -top-16 left-1/2 z-50 flex -translate-x-1/2 gap-1 rounded-full p-1.5"
		>
			{#each RATINGS as option (option.value)}
				<button
					class="flex w-20 flex-col items-center gap-1 rounded-full px-2 py-1.5 text-[10px] {option.value ===
					rating
						? 'bg-base-content/10 text-white'
						: 'text-white/70'}"
					onclick={() => rate(option.value)}
				>
					<option.icon size={22} weight={option.value === rating ? 'fill' : 'regular'} />
					{option.label}
				</button>
			{/each}
		</div>
	{/if}
</div>

<style>
	.rate-menu {
		animation: rate-in 220ms cubic-bezier(0.34, 1.3, 0.64, 1);
	}

	@keyframes rate-in {
		from {
			opacity: 0;
			scale: 0.9;
		}
		to {
			opacity: 1;
			scale: 1;
		}
	}
</style>
