<script lang="ts">
	// Plugins as a glass list: icon, name, what it does and an on/off switch;
	// tapping a row opens its details and settings.
	import CaretRightIcon from 'phosphor-svelte/lib/CaretRightIcon';
	import PlugsIcon from 'phosphor-svelte/lib/PlugsIcon';
	import WarningIcon from 'phosphor-svelte/lib/WarningIcon';
	import { setPluginEnabled, type PluginInfo } from '@neoworks-dev/otter-app/plugins';
	import ScreenHeader from '$lib/components/ScreenHeader.svelte';
	import Toggle from '$lib/components/Toggle.svelte';
	import PluginIcon from '$lib/components/plugins/PluginIcon.svelte';
	import PluginSheet from '$lib/components/plugins/PluginSheet.svelte';
	import { notifications } from '$lib/stores/notifications';

	let { data } = $props();

	let plugins = $state<PluginInfo[]>(data.plugins);
	let toggling = $state<string | null>(null);
	let openName = $state<string | null>(null);

	let openPlugin = $derived(plugins.find((plugin) => plugin.name === openName));
	let enabledCount = $derived(plugins.filter((plugin) => plugin.enabled).length);

	function displayName(plugin: PluginInfo): string {
		if (plugin.displayName) return plugin.displayName;
		return plugin.name;
	}

	function needsSetup(plugin: PluginInfo): boolean {
		return plugin.settings.some((field) => field.required && !field.value?.trim());
	}

	function replacePlugin(updated: PluginInfo) {
		plugins = plugins.map((plugin) => {
			if (plugin.name === updated.name) return updated;
			return plugin;
		});
	}

	function toggleOpenPlugin() {
		if (openPlugin) toggle(openPlugin);
	}

	async function toggle(plugin: PluginInfo) {
		toggling = plugin.name;
		try {
			replacePlugin(await setPluginEnabled(plugin.name, !plugin.enabled));
		} catch (toggleError) {
			notifications.push(String(toggleError), 'error');
		} finally {
			toggling = null;
		}
	}
</script>

{#if openPlugin}
	<PluginSheet
		plugin={openPlugin}
		toggling={toggling === openPlugin.name}
		ontoggle={toggleOpenPlugin}
		onsaved={replacePlugin}
		onclose={() => (openName = null)}
	/>
{/if}

<div class="-mx-3">
	<ScreenHeader title="Plugins" onback={() => history.back()} />
</div>

{#if plugins.length > 0}
	<p class="px-1 pb-3 text-xs text-white/50">{enabledCount} of {plugins.length} enabled</p>
	<div class="glass-bar mb-8 flex flex-col divide-y divide-white/10 rounded-3xl px-4">
		{#each plugins as plugin (plugin.name)}
			<div class="flex items-center gap-3 py-3" class:opacity-55={!plugin.enabled}>
				<button
					class="flex min-w-0 flex-1 items-center gap-3 text-left"
					onclick={() => (openName = plugin.name)}
				>
					<PluginIcon icon={plugin.icon} />
					<span class="flex min-w-0 flex-1 flex-col">
						<span class="flex items-center gap-1.5 truncate text-sm font-semibold">
							{displayName(plugin)}
							{#if needsSetup(plugin)}<WarningIcon
									size={14}
									weight="fill"
									class="text-warning shrink-0"
								/>{/if}
						</span>
						<span class="truncate text-xs text-white/45">
							{plugin.capabilities.join(' · ')} · v{plugin.version}
						</span>
					</span>
					<CaretRightIcon size={16} class="shrink-0 opacity-40" />
				</button>
				<Toggle
					checked={plugin.enabled}
					disabled={toggling === plugin.name}
					ariaLabel="Enable {displayName(plugin)}"
					onchange={() => toggle(plugin)}
				/>
			</div>
		{/each}
	</div>
{:else}
	<div class="flex flex-col items-center gap-3 pt-16 text-center">
		<PlugsIcon size={56} class="opacity-40" />
		<p class="font-semibold">No plugins loaded</p>
		<p class="max-w-xs text-sm opacity-60">Plugin bundles in the plugins folder show up here.</p>
	</div>
{/if}
