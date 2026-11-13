<script lang="ts">
	// Neoworks sign-in lands here: otter.letsmoe.com/auth/callback forwards the
	// browser to the origin the sign-in started from (state "web.<origin>.<nonce>").
	// The code works once, so it is only used in the browser, never during SSR.
	import { onMount } from 'svelte';
	import { goto } from '$app/navigation';
	import { page } from '$app/state';
	import { otter } from '@letsmoe/otter-app/api';

	let error = $state('');

	function refusal(reason: string | null): string {
		if (reason === 'access_denied') return 'The sign-in was cancelled.';
		return 'Neoworks did not complete the sign-in.';
	}

	onMount(async () => {
		const query = page.url.searchParams;
		const code = query.get('code');
		const state = query.get('state');
		if (query.get('error') || !code || !state) {
			error = refusal(query.get('error'));
			return;
		}
		try {
			await otter.mutation({
				completeNeoworksSignIn: { __args: { code, state }, signedIn: true }
			});
			await goto('/settings', { replaceState: true });
		} catch (failure) {
			error = 'Signing in to Neoworks failed.';
			if (failure instanceof Error) error = failure.message;
		}
	});
</script>

<div class="mx-auto flex max-w-md flex-col items-center gap-4 px-6 py-24 text-center">
	{#if error}
		<h1 class="text-xl font-semibold">Sign-in failed</h1>
		<p class="text-sm opacity-70">{error}</p>
		<a class="rounded-full bg-white px-4 py-2 text-sm font-semibold text-black" href="/settings">
			Back to Settings
		</a>
	{:else}
		<h1 class="text-xl font-semibold">Signing in to Neoworks…</h1>
	{/if}
</div>
