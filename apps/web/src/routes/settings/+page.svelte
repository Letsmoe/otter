<script lang="ts">
	import { invalidateAll } from '$app/navigation';
	import { otter } from '@neoworks-dev/otter-app/api';
	import Toggle from '$lib/components/Toggle.svelte';
	import GearSixIcon from 'phosphor-svelte/lib/GearSixIcon';

	let { data } = $props();
	let s = $state({ ...data.settings });
	let saving = $state(false);
	let saved = $state(false);
	let error = $state('');

	function errorMessage(saveError: unknown): string {
		if (saveError instanceof Error) return saveError.message;
		return 'Save failed';
	}

	async function save() {
		saving = true;
		error = '';
		saved = false;
		try {
			await otter.mutation({
				updateSettings: {
					__args: {
						input: {
							tmdbApiKey: s.tmdbApiKey,
							tmdbLanguage: s.tmdbLanguage,
							requireVpn: s.requireVpn
						}
					},
					tmdbApiKey: true
				}
			});
			await invalidateAll();
			saved = true;
			setTimeout(() => (saved = false), 2000);
		} catch (saveError) {
			error = errorMessage(saveError);
		} finally {
			saving = false;
		}
	}

	const inputClass =
		'w-full rounded-md border border-base-300 bg-base-100 px-3 py-2 text-sm outline-none transition-colors focus:border-base-content/30';
	const labelClass = 'text-[13px] font-medium text-base-content/80';
</script>

<div class="flex w-full flex-col gap-6">
	<div class="flex items-center gap-3">
		<span class="text-base-content/70"><GearSixIcon size={26} /></span>
		<h1 class="text-2xl font-bold tracking-tight">Settings</h1>
	</div>

	<!-- Integrations -->
	<section class="border-base-300 bg-base-200 flex flex-col gap-4 rounded-xl border p-5">
		<h2 class="text-sm font-semibold">Integrations</h2>

		<div class="flex flex-col gap-1.5">
			<div class="flex items-center justify-between">
				<label class={labelClass} for="tmdb-key">TMDB API key</label>
				<a
					href="https://www.themoviedb.org/settings/api"
					target="_blank"
					rel="noreferrer"
					class="text-base-content/40 hover:text-base-content/70 text-xs underline">Get key</a
				>
			</div>
			<input
				id="tmdb-key"
				type="password"
				class="{inputClass} max-w-md"
				bind:value={s.tmdbApiKey}
				placeholder="Using the built-in key"
			/>
			<p class="text-base-content/40 text-xs">Leave empty to use the key Otter ships with.</p>
		</div>

		<div class="flex flex-col gap-1.5">
			<label class={labelClass} for="tmdb-lang">TMDB language</label>
			<input id="tmdb-lang" type="text" class="{inputClass} max-w-xs" bind:value={s.tmdbLanguage} placeholder="de-DE" />
			<p class="text-base-content/40 text-xs">BCP-47 code, e.g. de-DE, en-US</p>
		</div>
	</section>

	<!-- Streaming -->
	<section class="border-base-300 bg-base-200 flex flex-col gap-4 rounded-xl border p-5">
		<h2 class="text-sm font-semibold">Streaming</h2>

		<label class="flex items-center justify-between gap-4 py-1">
			<span class={labelClass}>Require VPN for stream lookups and playback</span>
			<Toggle checked={s.requireVpn} onchange={(value) => (s.requireVpn = value)} />
		</label>
	</section>

	{#if error}
		<div class="rounded-lg border border-[#f87171]/30 bg-[#f87171]/10 px-4 py-2.5 text-sm text-[#f87171]">
			{error}
		</div>
	{/if}

	<div class="flex items-center gap-4 pb-2">
		<button
			class="flex items-center gap-2 rounded-md bg-white px-4 py-2 text-sm font-semibold text-black transition-opacity hover:opacity-90 disabled:opacity-50"
			onclick={save}
			disabled={saving}
		>
			{#if saving}<span class="loading loading-spinner loading-xs"></span>{/if}
			{saving ? 'Saving…' : 'Save settings'}
		</button>
		{#if saved}
			<span class="text-sm font-medium text-[#4ade80]">Saved ✓</span>
		{/if}
	</div>
</div>
