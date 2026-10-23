import { otter } from './api';

export interface PluginSettingField {
	key: string;
	label: string;
	description: string | null;
	type: string;
	required: boolean;
	default: string | null;
	value: string | null;
}

export interface PluginInfo {
	name: string;
	displayName: string | null;
	description: string | null;
	icon: string | null;
	version: string;
	capabilities: string[];
	enabled: boolean;
	// False when the plugin needs something missing on this host.
	available: boolean;
	settings: PluginSettingField[];
}

const PLUGIN_SELECT = {
	name: true,
	displayName: true,
	description: true,
	icon: true,
	version: true,
	capabilities: true,
	enabled: true,
	available: true,
	settings: {
		key: true,
		label: true,
		description: true,
		type: true,
		required: true,
		default: true,
		value: true
	}
} as const;

export async function fetchPlugins(): Promise<PluginInfo[]> {
	try {
		const data = await otter.query({ plugins: PLUGIN_SELECT });
		return data.plugins;
	} catch {
		return [];
	}
}

export async function setPluginEnabled(name: string, enabled: boolean): Promise<PluginInfo> {
	const data = await otter.mutation({
		setPluginEnabled: { __args: { name, enabled }, ...PLUGIN_SELECT }
	});
	return data.setPluginEnabled;
}

export async function savePluginSettings(
	name: string,
	values: { key: string; value: string }[]
): Promise<PluginInfo> {
	const data = await otter.mutation({
		setPluginSettings: { __args: { name, values }, ...PLUGIN_SELECT }
	});
	return data.setPluginSettings;
}
