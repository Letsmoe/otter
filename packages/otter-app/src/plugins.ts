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
	// Set when the plugin's site needs the user's account.
	account: PluginAccount | null;
}

export interface PluginAccount {
	signupUrl: string | null;
	// Null while no account is saved.
	username: string | null;
	loggedIn: boolean;
	// Why the last login failed.
	error: string | null;
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
	},
	account: {
		signupUrl: true,
		username: true,
		loggedIn: true,
		error: true
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

/** Logs in to the plugin's site; the backend keeps the account only when the site accepts it. */
export async function loginPlugin(name: string, username: string, password: string): Promise<PluginInfo> {
	const data = await otter.mutation({
		loginPlugin: { __args: { name, username, password }, ...PLUGIN_SELECT }
	});
	return data.loginPlugin;
}

export async function logoutPlugin(name: string): Promise<PluginInfo> {
	const data = await otter.mutation({
		logoutPlugin: { __args: { name }, ...PLUGIN_SELECT }
	});
	return data.logoutPlugin;
}
