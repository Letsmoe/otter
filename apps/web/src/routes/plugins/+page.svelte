<script lang="ts">
	import PlugsIcon from 'phosphor-svelte/lib/PlugsIcon';
	import GearSixIcon from 'phosphor-svelte/lib/GearSixIcon';
	import WarningIcon from 'phosphor-svelte/lib/WarningIcon';
	import XIcon from 'phosphor-svelte/lib/XIcon';
	import Toggle from '$lib/components/Toggle.svelte';
	import { setPluginEnabled, savePluginSettings, type PluginInfo } from '@neoworks-dev/otter-app/plugins';

	let { data } = $props();

	let plugins = $state<PluginInfo[]>(data.plugins);
	let toggling = $state<string | null>(null);
	let toast = $state<{ msg: string; ok: boolean } | null>(null);

	// Per-plugin settings editing state: field key → draft value.
	let editingSettings = $state<string | null>(null);
	let settingsDraft = $state<Record<string, string>>({});
	let savingSettings = $state(false);

	let editingPlugin = $derived(plugins.find((plugin) => plugin.name === editingSettings));

	// Soft context tones for capability pills.
	const capTones: Record<string, string> = {
		streams: 'bg-[#60a5fa]/15 text-[#60a5fa]',
		resolve: 'bg-[#fbbf24]/15 text-[#fbbf24]'
	};

	function capTone(cap: string): string {
		const tone = capTones[cap];
		if (tone) return tone;
		return 'bg-base-300 text-base-content/60';
	}

	function displayName(plugin: PluginInfo): string {
		if (plugin.displayName) return plugin.displayName;
		return plugin.name;
	}

	// Plugin icons are either an image URL or an emoji.
	function iconIsUrl(icon: string): boolean {
		return icon.startsWith('http://') || icon.startsWith('https://') || icon.startsWith('/');
	}

	function showToast(msg: string, ok: boolean) {
		toast = { msg, ok };
		setTimeout(() => (toast = null), 3000);
	}

	function replacePlugin(updated: PluginInfo) {
		plugins = plugins.map((plugin) => {
			if (plugin.name === updated.name) return updated;
			return plugin;
		});
	}

	async function toggle(plugin: PluginInfo) {
		toggling = plugin.name;
		try {
			replacePlugin(await setPluginEnabled(plugin.name, !plugin.enabled));
		} catch (toggleError) {
			showToast(String(toggleError), false);
		} finally {
			toggling = null;
		}
	}

	// Seed the draft with stored values, falling back to declared defaults.
	function fieldValue(field: PluginInfo['settings'][number]): string {
		if (field.value !== null) return field.value;
		if (field.default !== null) return field.default;
		return '';
	}

	function openSettings(plugin: PluginInfo) {
		editingSettings = plugin.name;
		const draft: Record<string, string> = {};
		for (const field of plugin.settings) draft[field.key] = fieldValue(field);
		settingsDraft = draft;
	}

	function closeSettings() {
		editingSettings = null;
		settingsDraft = {};
	}

	async function saveSettings(plugin: PluginInfo) {
		savingSettings = true;
		try {
			const values = Object.entries(settingsDraft).map(([key, value]) => ({ key, value }));
			replacePlugin(await savePluginSettings(plugin.name, values));
			showToast('Settings saved', true);
			closeSettings();
		} catch (saveError) {
			showToast(String(saveError), false);
		} finally {
			savingSettings = false;
		}
	}

	function hasRequiredMissing(plugin: PluginInfo): boolean {
		return plugin.settings.some((field) => field.required && !field.value?.trim());
	}
</script>

{#if toast}
	<div class="fixed right-4 top-4 z-[70]">
		<div
			class="rounded-lg border px-4 py-2.5 text-sm shadow-lg {toast.ok
				? 'border-[#4ade80]/30 bg-[#4ade80]/10 text-[#4ade80]'
				: 'border-[#f87171]/30 bg-[#f87171]/10 text-[#f87171]'}"
		>
			{toast.msg}
		</div>
	</div>
{/if}

<!-- Settings modal -->
{#if editingPlugin}
	{@const plugin = editingPlugin}
	<div class="fixed inset-0 z-[60] flex items-center justify-center p-4">
		<button type="button" class="absolute inset-0 bg-black/50" aria-label="Close" onclick={closeSettings}
		></button>
		<div
			class="border-base-300 bg-base-200 relative z-10 w-full max-w-md rounded-xl border p-5 shadow-2xl"
		>
			<div class="mb-4 flex items-center justify-between">
				<div class="flex items-center gap-3">
					{#if plugin.icon && iconIsUrl(plugin.icon)}
						<img
							src={plugin.icon}
							alt=""
							class="h-7 w-7 rounded object-contain"
							onerror={(e) => ((e.currentTarget as HTMLImageElement).style.display = 'none')}
						/>
					{:else if plugin.icon}
						<span class="text-xl">{plugin.icon}</span>
					{/if}
					<h3 class="text-base font-semibold">{displayName(plugin)} settings</h3>
				</div>
				<button
					type="button"
					class="text-base-content/40 hover:text-base-content flex h-7 w-7 items-center justify-center rounded-md transition-colors"
					onclick={closeSettings}
					aria-label="Close"
				>
					<XIcon size={16} />
				</button>
			</div>

			<div class="flex flex-col gap-4">
				{#each plugin.settings as field (field.key)}
					<div class="flex flex-col gap-1.5">
						<label class="text-base-content/80 text-[13px] font-medium" for="setting-{field.key}">
							{field.label}
							{#if field.required}<span class="text-[#f87171]">*</span>{/if}
						</label>
						{#if field.type === 'boolean'}
							<Toggle
								checked={settingsDraft[field.key] === 'true'}
								ariaLabel={field.label}
								onchange={(value) => (settingsDraft[field.key] = value ? 'true' : 'false')}
							/>
						{:else}
							<input
								id="setting-{field.key}"
								type={field.type === 'password' ? 'password' : 'text'}
								class="border-base-300 bg-base-100 focus:border-base-content/30 w-full rounded-md border px-3 py-2 text-sm outline-none transition-colors"
								placeholder={field.default ?? ''}
								bind:value={settingsDraft[field.key]}
							/>
						{/if}
						{#if field.description}
							<p class="text-base-content/40 text-xs">{field.description}</p>
						{/if}
					</div>
				{/each}
			</div>

			<div class="mt-5 flex justify-end gap-2">
				<button
					class="text-base-content/70 hover:bg-base-300 rounded-md px-3 py-2 text-sm font-medium transition-colors"
					onclick={closeSettings}
					disabled={savingSettings}
				>
					Cancel
				</button>
				<button
					class="flex items-center gap-2 rounded-md bg-white px-4 py-2 text-sm font-semibold text-black transition-opacity hover:opacity-90 disabled:opacity-50"
					onclick={() => saveSettings(plugin)}
					disabled={savingSettings}
				>
					{#if savingSettings}<span class="loading loading-spinner loading-xs"></span>{/if}
					Save
				</button>
			</div>
		</div>
	</div>
{/if}

<div class="flex w-full flex-col gap-8 py-2">
	<div class="flex items-center gap-3">
		<span class="text-base-content/70"><PlugsIcon size={26} /></span>
		<div>
			<h1 class="text-2xl font-bold tracking-tight">Plugins</h1>
			<p class="text-base-content/40 text-sm">{plugins.length} loaded</p>
		</div>
	</div>

	{#if plugins.length === 0}
		<div
			class="border-base-300 flex flex-col items-center gap-3 rounded-xl border border-dashed py-24 text-center"
		>
			<span class="text-base-content/20"><PlugsIcon size={48} /></span>
			<p class="text-base-content/50 text-sm">No plugins loaded</p>
			<p class="text-base-content/30 text-xs">Drop plugin bundles into the plugins directory</p>
		</div>
	{:else}
		<div class="grid gap-3 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 2xl:grid-cols-5">
			{#each plugins as plugin (plugin.name)}
				<div
					class="border-base-300 bg-base-200 flex flex-col gap-3 rounded-xl border p-4 transition-opacity {plugin.enabled
						? ''
						: 'opacity-55'}"
				>
					<!-- Header -->
					<div class="flex items-start justify-between gap-3">
						<div class="flex min-w-0 items-center gap-3">
							{#if plugin.icon && iconIsUrl(plugin.icon)}
								<img
									src={plugin.icon}
									alt=""
									class="h-9 w-9 shrink-0 rounded-lg object-contain"
									onerror={(e) => ((e.currentTarget as HTMLImageElement).style.display = 'none')}
								/>
							{:else if plugin.icon}
								<span
									class="bg-base-300 flex h-9 w-9 shrink-0 items-center justify-center rounded-lg text-lg"
								>
									{plugin.icon}
								</span>
							{:else}
								<span
									class="bg-base-300 text-base-content/50 flex h-9 w-9 shrink-0 items-center justify-center rounded-lg"
								>
									<PlugsIcon size={18} />
								</span>
							{/if}
							<div class="min-w-0">
								<h2 class="truncate text-sm font-semibold">{displayName(plugin)}</h2>
								<p class="text-base-content/40 font-mono text-xs">v{plugin.version}</p>
								{#if !plugin.available}
									<p class="text-xs text-[#fbbf24]">Unavailable on this host</p>
								{/if}
							</div>
						</div>
						<div class="mt-0.5">
							<Toggle
								checked={plugin.enabled}
								disabled={toggling === plugin.name}
								ariaLabel="Enable {displayName(plugin)}"
								onchange={() => toggle(plugin)}
							/>
						</div>
					</div>

					{#if plugin.description}
						<p class="text-base-content/55 line-clamp-2 text-[13px] leading-relaxed">
							{plugin.description}
						</p>
					{/if}

					<!-- Capabilities -->
					<div class="flex flex-wrap gap-1.5">
						{#each plugin.capabilities as cap (cap)}
							<span class="rounded-full px-2 py-0.5 text-[11px] font-medium {capTone(cap)}">
								{cap}
							</span>
						{/each}
					</div>

					<!-- Actions -->
					<div class="mt-auto flex gap-2 pt-1">
						{#if plugin.settings.length > 0}
							<button
								class="flex flex-1 items-center justify-center gap-1.5 rounded-md px-3 py-1.5 text-[13px] font-medium transition-colors {hasRequiredMissing(
									plugin
								)
									? 'bg-[#fbbf24]/15 text-[#fbbf24] hover:bg-[#fbbf24]/25'
									: 'text-base-content/70 hover:bg-base-300'}"
								onclick={() => openSettings(plugin)}
							>
								{#if hasRequiredMissing(plugin)}
									<WarningIcon size={14} weight="fill" /> Configure
								{:else}
									<GearSixIcon size={14} /> Settings
								{/if}
							</button>
						{/if}
					</div>
				</div>
			{/each}
		</div>
	{/if}
</div>
