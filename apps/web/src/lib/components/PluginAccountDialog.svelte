<script lang="ts">
	import XIcon from 'phosphor-svelte/lib/XIcon';
	import { loginPlugin, logoutPlugin, type PluginInfo } from '@letsmoe/otter-app/plugins';

	interface Props {
		plugin: PluginInfo;
		onclose: () => void;
		onchange: (updated: PluginInfo) => void;
	}

	let { plugin, onclose, onchange }: Props = $props();

	let username = $state('');
	let password = $state('');
	let busy = $state(false);
	let error = $state('');

	let account = $derived(plugin.account);
	let loggedIn = $derived(account !== null && account.loggedIn && account.username !== null);

	$effect(() => {
		username = savedUsername(plugin);
	});

	function savedUsername(current: PluginInfo): string {
		if (current.account === null || current.account.username === null) return '';
		return current.account.username;
	}

	function pluginName(current: PluginInfo): string {
		if (current.displayName) return current.displayName;
		return current.name;
	}

	function errorMessage(caught: unknown): string {
		if (caught instanceof Error) return caught.message;
		return String(caught);
	}

	async function login() {
		busy = true;
		error = '';
		try {
			onchange(await loginPlugin(plugin.name, username.trim(), password));
			password = '';
		} catch (caught) {
			error = errorMessage(caught);
		} finally {
			busy = false;
		}
	}

	async function logout() {
		busy = true;
		error = '';
		try {
			onchange(await logoutPlugin(plugin.name));
		} catch (caught) {
			error = errorMessage(caught);
		} finally {
			busy = false;
		}
	}

	const inputClass =
		'border-base-300 bg-base-100 focus:border-base-content/30 w-full rounded-md border px-3 py-2 text-sm outline-none transition-colors';
</script>

<div class="fixed inset-0 z-[60] flex items-center justify-center p-4">
	<button type="button" class="absolute inset-0 bg-black/50" aria-label="Close" onclick={onclose}></button>
	<div class="border-base-300 bg-base-200 relative z-10 flex w-full max-w-md flex-col gap-4 rounded-xl border p-5 shadow-2xl">
		<div class="flex items-center justify-between">
			<h3 class="text-base font-semibold">{pluginName(plugin)} account</h3>
			<button
				type="button"
				class="text-base-content/40 hover:text-base-content flex h-7 w-7 items-center justify-center rounded-md transition-colors"
				onclick={onclose}
				aria-label="Close"
			>
				<XIcon size={16} />
			</button>
		</div>

		{#if loggedIn}
			<div class="flex items-center justify-between gap-4">
				<p class="text-base-content/75 text-sm">Logged in as {account?.username}</p>
				<button
					class="rounded-md px-3 py-2 text-sm font-medium text-[#f87171] transition-colors hover:bg-[#f87171]/10"
					onclick={logout}
					disabled={busy}
				>
					Log out
				</button>
			</div>
		{:else}
			{#if account?.error}
				<p class="text-xs text-[#f87171]">Login stopped working: {account.error}</p>
			{/if}
			<form class="flex flex-col gap-3" onsubmit={(event) => { event.preventDefault(); login(); }}>
				<label class="flex flex-col gap-1.5 text-[13px] font-medium">
					Username
					<input class={inputClass} autocomplete="username" bind:value={username} required />
				</label>
				<label class="flex flex-col gap-1.5 text-[13px] font-medium">
					Password
					<input class={inputClass} type="password" autocomplete="current-password" bind:value={password} required />
				</label>
				<p class="text-base-content/40 text-xs">
					Otter keeps your password encrypted so it can log in again when the site signs you out.
				</p>
				{#if account?.signupUrl}
					<a class="text-base-content/60 text-xs underline" href={account.signupUrl} target="_blank" rel="noreferrer">
						No account yet? Sign up
					</a>
				{/if}
				<button
					type="submit"
					class="flex items-center justify-center gap-2 rounded-md bg-white px-4 py-2 text-sm font-semibold text-black transition-opacity hover:opacity-90 disabled:opacity-50"
					disabled={busy}
				>
					{#if busy}<span class="loading loading-spinner loading-xs"></span>{/if}
					Log in
				</button>
			</form>
		{/if}

		{#if error}
			<p class="text-sm text-[#f87171]">{error}</p>
		{/if}
	</div>
</div>
