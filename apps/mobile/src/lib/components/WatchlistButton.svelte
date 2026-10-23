<script lang="ts">
	import BookmarkSimpleIcon from 'phosphor-svelte/lib/BookmarkSimpleIcon';
	import CheckIcon from 'phosphor-svelte/lib/CheckIcon';
	import { activeProfile } from '@neoworks-dev/otter-app/profile';
	import { notifications } from '$lib/stores/notifications';
	import { addToWatchlist, removeFromWatchlist, fetchInWatchlist } from '@neoworks-dev/otter-app/discovery';

	interface Props {
		mediaId: string;
		class?: string;
	}

	let { mediaId, class: className = '' }: Props = $props();

	let inWatchlist = $state(false);
	let busy = $state(false);

	// Reflect current membership; re-checks when the profile or media changes.
	$effect(() => {
		const profile = $activeProfile;
		if (!profile || !mediaId) {
			inWatchlist = false;
			return;
		}
		let cancelled = false;
		fetchInWatchlist(profile.id, mediaId).then((result) => {
			if (!cancelled) inWatchlist = result;
		});
		return () => {
			cancelled = true;
		};
	});

	async function toggle() {
		const profile = $activeProfile;
		if (!profile || !mediaId || busy) return;
		busy = true;
		const next = !inWatchlist;
		inWatchlist = next;
		try {
			if (next) {
				await addToWatchlist(profile.id, mediaId);
				notifications.push('Added to watchlist', 'success');
			} else {
				await removeFromWatchlist(profile.id, mediaId);
				notifications.push('Removed from watchlist', 'success');
			}
		} catch {
			inWatchlist = !next;
			notifications.push('Could not update watchlist', 'error');
		} finally {
			busy = false;
		}
	}
</script>

<button
	type="button"
	onclick={toggle}
	disabled={busy}
	class="flex items-center gap-2 rounded-lg bg-white/10 px-5 py-2.5 text-sm font-semibold text-white transition-colors hover:bg-white/20 disabled:opacity-50 {className}"
>
	{#if inWatchlist}
		<CheckIcon size={16} weight="bold" />
		In watchlist
	{:else}
		<BookmarkSimpleIcon size={16} />
		Add to watchlist
	{/if}
</button>
