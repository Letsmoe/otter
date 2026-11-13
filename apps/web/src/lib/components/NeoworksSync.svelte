<script lang="ts">
	import { onMount } from 'svelte';
	import { otter } from '@letsmoe/otter-app/api';

	interface SyncResult {
		at: string;
		received: number;
		sent: number;
		failure?: string | null;
	}

	interface Account {
		signedIn: boolean;
		email?: string | null;
		name?: string | null;
		lastSync?: SyncResult | null;
	}

	const accountFields = {
		signedIn: true,
		email: true,
		name: true,
		lastSync: { at: true, received: true, sent: true, failure: true }
	} as const;

	let account = $state<Account | null>(null);
	let busy = $state(false);
	let error = $state('');

	function messageOf(failure: unknown, fallback: string): string {
		if (failure instanceof Error) return failure.message;
		return fallback;
	}

	function accountLine(current: Account | null): string {
		if (!current) return 'Checking…';
		if (!current.signedIn) return 'Not signed in';
		if (current.email) return `Signed in as ${current.email}`;
		if (current.name) return `Signed in as ${current.name}`;
		return 'Signed in';
	}

	onMount(async () => {
		try {
			const data = await otter.query({ neoworksAccount: accountFields });
			account = data.neoworksAccount;
		} catch (failure) {
			error = messageOf(failure, 'Could not check the Neoworks account');
		}
	});

	// Neoworks returns to otter.letsmoe.com, which sends the browser on to this
	// origin's /auth/callback (routes/auth/callback).
	async function signIn() {
		busy = true;
		error = '';
		try {
			const data = await otter.mutation({
				startNeoworksSignIn: { __args: { returnTo: window.location.origin } }
			});
			window.location.href = data.startNeoworksSignIn;
		} catch (failure) {
			error = messageOf(failure, 'Could not start the sign-in');
			busy = false;
		}
	}

	function syncLine(lastSync: SyncResult | null | undefined): string {
		if (!lastSync) return 'Not synced yet; syncs every 10 minutes';
		const time = new Date(lastSync.at).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
		if (lastSync.failure) return `Sync failed at ${time}: ${lastSync.failure}`;
		return `Synced at ${time}: ${lastSync.received} received, ${lastSync.sent} sent`;
	}

	async function syncNow() {
		busy = true;
		error = '';
		try {
			const data = await otter.mutation({ syncNeoworks: accountFields });
			account = data.syncNeoworks;
		} catch (failure) {
			error = messageOf(failure, 'Sync failed');
		}
		busy = false;
	}

	async function signOut() {
		busy = true;
		error = '';
		try {
			const data = await otter.mutation({ signOutOfNeoworks: accountFields });
			account = data.signOutOfNeoworks;
		} catch (failure) {
			error = messageOf(failure, 'Could not sign out');
		}
		busy = false;
	}
</script>

<section class="border-base-300 bg-base-200 flex flex-col gap-4 rounded-xl border p-5">
	<h2 class="text-sm font-semibold">Sync</h2>

	<div class="flex items-center justify-between gap-4 py-1">
		<div class="flex flex-col">
			<span class="text-sm">Neoworks</span>
			<span class="text-xs opacity-50">{accountLine(account)}</span>
		</div>
		{#if account?.signedIn}
			<button
				type="button"
				class="rounded-full border border-white/20 px-4 py-1.5 text-sm transition-opacity hover:opacity-80 disabled:opacity-50"
				onclick={signOut}
				disabled={busy}
			>
				Sign out
			</button>
		{:else}
			<button
				type="button"
				class="rounded-full bg-white px-4 py-1.5 text-sm font-semibold text-black transition-opacity hover:opacity-90 disabled:opacity-50"
				onclick={signIn}
				disabled={busy || !account}
			>
				Sign in
			</button>
		{/if}
	</div>
	{#if account?.signedIn}
		<div class="flex items-center justify-between gap-4 py-1">
			<span class="text-xs opacity-70">
				{#if busy}Syncing…{:else}{syncLine(account.lastSync)}{/if}
			</span>
			<button
				type="button"
				class="rounded-full border border-white/20 px-4 py-1.5 text-sm transition-opacity hover:opacity-80 disabled:opacity-50"
				onclick={syncNow}
				disabled={busy}
			>
				Sync now
			</button>
		</div>
	{/if}
	{#if error}
		<p class="text-xs text-[#f87171]">{error}</p>
	{/if}
	<p class="text-xs opacity-50">
		One Neoworks account per household, end-to-end encrypted. Profiles sync between its devices
		now; watch history, My List, ratings and settings follow in a later update.
	</p>
</section>
