<script lang="ts">
	import { get } from 'svelte/store';
	import type { Component } from 'svelte';
	import SmileySadIcon from 'phosphor-svelte/lib/SmileySadIcon';
	import SmileyMehIcon from 'phosphor-svelte/lib/SmileyMehIcon';
	import SmileyIcon from 'phosphor-svelte/lib/SmileyIcon';
	import SmileyWinkIcon from 'phosphor-svelte/lib/SmileyWinkIcon';
	import { activeProfile } from '@neoworks-dev/otter-app/profile';
	import { fetchUserRating, setUserRating, clearUserRating } from '@neoworks-dev/otter-app/discovery';

	interface Props {
		mediaId: string;
	}

	let { mediaId }: Props = $props();

	// Four buckets mapped onto the backend's 1–10 rating scale.
	const buckets: { label: string; icon: Component; value: number }[] = [
		{ label: 'Bad', icon: SmileySadIcon, value: 2 },
		{ label: 'Mediocre', icon: SmileyMehIcon, value: 4.5 },
		{ label: 'Good', icon: SmileyIcon, value: 7.5 },
		{ label: 'Very good', icon: SmileyWinkIcon, value: 10 }
	];

	let current = $state<number | null>(null);
	let busy = $state(false);

	$effect(() => {
		const profile = get(activeProfile);
		const id = mediaId;
		if (!profile || !id) return;
		let cancelled = false;
		fetchUserRating(profile.id, id).then((rating) => {
			if (!cancelled) current = rating;
		});
		return () => {
			cancelled = true;
		};
	});

	async function choose(value: number) {
		const profile = get(activeProfile);
		if (!profile || busy) return;
		busy = true;
		const previous = current;
		// Clicking the active bucket clears the rating.
		const clearing = current === value;
		try {
			if (clearing) {
				current = null;
				await clearUserRating(profile.id, mediaId);
			} else {
				current = value;
				await setUserRating(profile.id, mediaId, value);
			}
		} catch {
			current = previous;
		} finally {
			busy = false;
		}
	}
</script>

<div class="flex flex-col gap-2">
	<span class="text-base-content/50 text-xs font-semibold tracking-wide uppercase">Your rating</span>
	<div class="flex flex-wrap gap-2">
		{#each buckets as bucket (bucket.value)}
			<button
				type="button"
				disabled={busy}
				title={bucket.label}
				aria-label={bucket.label}
				aria-pressed={current === bucket.value}
				onclick={() => choose(bucket.value)}
				class="flex h-10 w-10 items-center justify-center rounded-lg transition-colors disabled:opacity-50 {current ===
				bucket.value
					? 'bg-white text-black'
					: 'bg-white/10 text-white hover:bg-white/20'}"
			>
				<bucket.icon size={22} weight={current === bucket.value ? 'fill' : 'regular'} />
			</button>
		{/each}
	</div>
</div>
