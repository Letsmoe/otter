<script lang="ts">
	// A plugin's details in a bottom sheet: description, what it does, the
	// on/off switch and its settings.
	import { savePluginSettings, type PluginInfo } from '@neoworks-dev/otter-app/plugins';
	import BottomSheet from '$lib/components/BottomSheet.svelte';
	import Toggle from '$lib/components/Toggle.svelte';
	import { notifications } from '$lib/stores/notifications';
	import PluginIcon from './PluginIcon.svelte';

	const CAPABILITY_TEXT: Record<string, string> = {
		streams: 'Finds stream links for movies and episodes',
		resolve: 'Turns hoster links into playable video'
	};

	interface Props {
		plugin: PluginInfo;
		toggling: boolean;
		ontoggle: () => void;
		onsaved: (updated: PluginInfo) => void;
		onclose: () => void;
	}

	let { plugin, toggling, ontoggle, onsaved, onclose }: Props = $props();

	let draft = $state<Record<string, string>>(initialDraft());
	let saving = $state(false);

	// Stored values first, then declared defaults.
	function initialDraft(): Record<string, string> {
		const values: Record<string, string> = {};
		for (const field of plugin.settings) values[field.key] = fieldValue(field);
		return values;
	}

	function fieldValue(field: PluginInfo['settings'][number]): string {
		if (field.value !== null) return field.value;
		if (field.default !== null) return field.default;
		return '';
	}

	function displayName(): string {
		if (plugin.displayName) return plugin.displayName;
		return plugin.name;
	}

	function capabilityText(capability: string): string {
		const text = CAPABILITY_TEXT[capability];
		if (text) return text;
		return capability;
	}

	function inputType(fieldType: string): string {
		if (fieldType === 'password') return 'password';
		return 'text';
	}

	function booleanText(value: boolean): string {
		if (value) return 'true';
		return 'false';
	}

	function errorMessage(saveError: unknown): string {
		if (saveError instanceof Error) return saveError.message;
		return 'Could not save';
	}

	async function save() {
		saving = true;
		try {
			const values = Object.entries(draft).map(([key, value]) => ({ key, value }));
			onsaved(await savePluginSettings(plugin.name, values));
			notifications.push('Settings saved', 'success');
			onclose();
		} catch (saveError) {
			notifications.push(errorMessage(saveError), 'error');
		} finally {
			saving = false;
		}
	}
</script>

<BottomSheet {onclose}>
	<div class="flex flex-col gap-5">
		<div class="flex items-center gap-4">
			<PluginIcon icon={plugin.icon} size="large" />
			<div class="flex min-w-0 flex-1 flex-col">
				<h2 class="truncate text-lg font-bold">{displayName()}</h2>
				<span class="font-mono text-xs text-white/45">v{plugin.version}</span>
			</div>
			<Toggle
				checked={plugin.enabled}
				disabled={toggling}
				ariaLabel="Enable {displayName()}"
				onchange={ontoggle}
			/>
		</div>

		{#if plugin.description}
			<p class="text-sm leading-relaxed text-white/75">{plugin.description}</p>
		{/if}
		{#if !plugin.available}
			<p class="text-warning text-sm">Unavailable on this device.</p>
		{/if}

		<ul class="flex flex-col gap-1.5 text-sm text-white/70">
			{#each plugin.capabilities as capability (capability)}
				<li class="flex items-center gap-2">
					<span class="h-1.5 w-1.5 rounded-full bg-white/50"></span>
					{capabilityText(capability)}
				</li>
			{/each}
		</ul>

		{#if plugin.settings.length > 0}
			<div class="flex flex-col divide-y divide-white/10 rounded-3xl bg-white/5 px-4">
				{#each plugin.settings as field (field.key)}
					{#if field.type === 'boolean'}
						<label class="flex items-center justify-between gap-4 py-3.5 text-sm">
							{field.label}
							<Toggle
								checked={draft[field.key] === 'true'}
								ariaLabel={field.label}
								onchange={(value) => (draft[field.key] = booleanText(value))}
							/>
						</label>
					{:else}
						<label class="flex flex-col gap-1 py-3">
							<span class="text-sm">
								{field.label}
								{#if field.required}<span class="text-error">*</span>{/if}
							</span>
							<input
								type={inputType(field.type)}
								class="bg-transparent text-sm text-white/75 outline-none placeholder:text-white/30"
								placeholder={field.default || ''}
								bind:value={draft[field.key]}
							/>
							{#if field.description}
								<span class="text-xs text-white/40">{field.description}</span>
							{/if}
						</label>
					{/if}
				{/each}
			</div>
			<button
				class="glass-pill flex h-12 items-center justify-center gap-2 rounded-full bg-white text-sm font-bold text-black disabled:opacity-50"
				disabled={saving}
				onclick={save}
			>
				{#if saving}<span class="loading loading-spinner loading-xs"></span>{/if}
				Save
			</button>
		{/if}
	</div>
</BottomSheet>
