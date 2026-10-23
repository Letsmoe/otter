<script lang="ts">
	// Three glass rating buttons (Not for me, I like this, Love this) for the player.
	import type { Component } from 'svelte';
	import { activeProfile } from '@neoworks-dev/otter-app/profile';
	import { clearUserRating, fetchUserRating, setUserRating } from '@neoworks-dev/otter-app/discovery';
	import ThumbsDownIcon from 'phosphor-svelte/lib/ThumbsDownIcon';
	import ThumbsUpIcon from 'phosphor-svelte/lib/ThumbsUpIcon';
	import HeartIcon from 'phosphor-svelte/lib/HeartIcon';

	const RATINGS: { label: string; value: number; icon: Component<{ size?: number; weight?: 'regular' | 'fill' }> }[] = [
		{ label: 'Not for me', value: 2, icon: ThumbsDownIcon },
		{ label: 'I like this', value: 7.5, icon: ThumbsUpIcon },
		{ label: 'Love this!', value: 10, icon: HeartIcon }
	];

	interface Props {
		mediaId: string;
		/** Keeps the controls up while rating. */
		oninteract?: () => void;
	}

	let { mediaId, oninteract }: Props = $props();

	let rating = $state<number | null>(null);

	$effect(() => {
		const profile = $activeProfile;
		if (!profile) return;
		fetchUserRating(profile.id, mediaId).then((value) => (rating = value));
	});

	function weightOf(value: number): 'fill' | 'regular' {
		if (value === rating) return 'fill';
		return 'regular';
	}

	// Choosing the current rating again clears it.
	async function rate(value: number) {
		oninteract?.();
		const profile = $activeProfile;
		if (!profile) return;
		if (rating === value) {
			await clearUserRating(profile.id, mediaId);
			rating = null;
			return;
		}
		await setUserRating(profile.id, mediaId, value);
		rating = value;
	}
</script>

<div class="flex gap-2">
	{#each RATINGS as option (option.value)}
		<button
			class="glass-bar flex h-11 w-11 items-center justify-center rounded-full"
			class:bg-white={option.value === rating}
			class:text-black={option.value === rating}
			aria-label={option.label}
			onclick={() => rate(option.value)}
		>
			<option.icon size={20} weight={weightOf(option.value)} />
		</button>
	{/each}
</div>
