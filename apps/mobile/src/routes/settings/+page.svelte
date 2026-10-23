<script lang="ts">
	// App settings as glass groups: server address (per device), TMDB, streaming
	// and profiles. Every change saves on its own; no Save button.
	import { onMount } from 'svelte';
	import { otter, checkServer, normalizeServerUrl } from '@neoworks-dev/otter-app/api';
	import { activeProfile } from '@neoworks-dev/otter-app/profile';
	import CaretRightIcon from 'phosphor-svelte/lib/CaretRightIcon';
	import Toggle from '$lib/components/Toggle.svelte';
	import FilterSelect from '$lib/components/FilterSelect.svelte';
	import ScreenHeader from '$lib/components/ScreenHeader.svelte';
	import SettingsGroup from '$lib/components/settings/SettingsGroup.svelte';
	import { notifications } from '$lib/stores/notifications';
	import { vpnStatus } from '$lib/stores/vpn';
	import {
		clearServerUrl,
		isNativeApp,
		resolveApiBase,
		saveServerUrl,
		storedServerUrl
	} from '$lib/server';

	const LANGUAGES = [
		{ value: 'en-US', label: 'English' },
		{ value: 'de-DE', label: 'Deutsch' },
		{ value: 'fr-FR', label: 'Français' },
		{ value: 'es-ES', label: 'Español' },
		{ value: 'it-IT', label: 'Italiano' },
		{ value: 'nl-NL', label: 'Nederlands' },
		{ value: 'pl-PL', label: 'Polski' },
		{ value: 'pt-BR', label: 'Português (Brasil)' },
		{ value: 'tr-TR', label: 'Türkçe' },
		{ value: 'ja-JP', label: '日本語' }
	];

	let { data } = $props();
	let settings = $state({ ...data.settings });
	let serverDraft = $state(storedServerUrl() || '');
	let checkingServer = $state(false);

	let languageOptions = $derived(withCurrentLanguage(settings.tmdbLanguage));

	onMount(() => {
		if (!serverDraft) serverDraft = resolveApiBase();
	});

	// Keeps an unlisted stored language selectable.
	function withCurrentLanguage(current: string) {
		if (LANGUAGES.some((language) => language.value === current)) return LANGUAGES;
		return [{ value: current, label: current }, ...LANGUAGES];
	}

	function errorMessage(saveError: unknown): string {
		if (saveError instanceof Error) return saveError.message;
		return 'Could not save';
	}

	async function save(input: { tmdbApiKey?: string; tmdbLanguage?: string; requireVpn?: boolean }) {
		try {
			await otter.mutation({ updateSettings: { __args: { input }, tmdbLanguage: true } });
			notifications.push('Saved', 'success');
		} catch (saveError) {
			notifications.push(errorMessage(saveError), 'error');
		}
	}

	function saveApiKey() {
		if (settings.tmdbApiKey === data.settings.tmdbApiKey) return;
		save({ tmdbApiKey: settings.tmdbApiKey });
	}

	function chooseLanguage(value: string) {
		settings.tmdbLanguage = value;
		save({ tmdbLanguage: value });
	}

	function setRequireVpn(value: boolean) {
		settings.requireVpn = value;
		save({ requireVpn: value });
	}

	// A new server address applies after a reload, once it answers.
	async function connectServer() {
		checkingServer = true;
		const reachable = await checkServer(serverDraft);
		checkingServer = false;
		if (!reachable) {
			notifications.push(`No Otter server at ${normalizeServerUrl(serverDraft)}`, 'error');
			return;
		}
		saveServerUrl(serverDraft);
		location.reload();
	}

	function useEmbeddedServer() {
		clearServerUrl();
		location.reload();
	}

	function vpnLine(): string {
		if (!$vpnStatus.known) return 'Checking…';
		if ($vpnStatus.connected) return 'Connected';
		return 'Not connected';
	}

	function connectLabel(): string {
		if (checkingServer) return 'Checking…';
		return 'Connect';
	}
</script>

<div class="-mx-3">
	<ScreenHeader title="App Settings" onback={() => history.back()} />
</div>

<div class="flex flex-col gap-6 pt-2 pb-8">
	<SettingsGroup
		title="Server"
		footer="The Otter backend this device talks to. Saved on this device only."
	>
		<div class="flex flex-col gap-3 py-3">
			<input
				type="url"
				inputmode="url"
				autocapitalize="off"
				class="w-full bg-transparent text-sm outline-none placeholder:text-white/30"
				placeholder="http://192.168.1.10:8080"
				bind:value={serverDraft}
			/>
			<div class="flex gap-2">
				<button
					class="glass-pill flex-1 rounded-full bg-white/10 py-2 text-sm font-semibold disabled:opacity-50"
					disabled={checkingServer || !serverDraft.trim()}
					onclick={connectServer}
				>
					{connectLabel()}
				</button>
				{#if isNativeApp() && storedServerUrl()}
					<button
						class="flex-1 rounded-full py-2 text-sm text-white/70"
						onclick={useEmbeddedServer}
					>
						Use this phone
					</button>
				{/if}
			</div>
		</div>
	</SettingsGroup>

	<SettingsGroup title="Metadata" footer="TMDB provides titles, posters and recommendations.">
		<label class="flex flex-col gap-1 py-3">
			<span class="flex items-center justify-between text-sm">
				TMDB API key
				<a
					href="https://www.themoviedb.org/settings/api"
					target="_blank"
					rel="noreferrer"
					class="text-xs text-white/40 underline">Get key</a
				>
			</span>
			<input
				type="password"
				class="w-full bg-transparent text-sm text-white/70 outline-none placeholder:text-white/30"
				placeholder="Paste your key"
				bind:value={settings.tmdbApiKey}
				onblur={saveApiKey}
			/>
		</label>
		<div class="flex items-center justify-between gap-4 py-3">
			<span class="text-sm">Language</span>
			<FilterSelect
				label="Language"
				showLabel={false}
				options={languageOptions}
				selected={settings.tmdbLanguage}
				onselect={chooseLanguage}
			/>
		</div>
	</SettingsGroup>

	<SettingsGroup title="Streaming">
		<label class="flex items-center justify-between gap-4 py-3.5">
			<span class="flex flex-col">
				<span class="text-sm">Require VPN</span>
				<span class="text-xs text-white/45">No lookups or playback without a VPN</span>
			</span>
			<Toggle checked={settings.requireVpn} ariaLabel="Require VPN" onchange={setRequireVpn} />
		</label>
		<div class="flex items-center justify-between py-3.5 text-sm">
			VPN
			<span class="text-white/60">{vpnLine()}</span>
		</div>
	</SettingsGroup>

	<SettingsGroup title="Profiles">
		<button
			class="flex items-center justify-between py-3.5 text-left text-sm"
			onclick={() => activeProfile.logout()}
		>
			Manage Profiles
			<CaretRightIcon size={18} class="opacity-60" />
		</button>
		<a href="/plugins" class="flex items-center justify-between py-3.5 text-sm">
			Plugins
			<CaretRightIcon size={18} class="opacity-60" />
		</a>
	</SettingsGroup>
</div>
