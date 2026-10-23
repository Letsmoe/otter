// The TMDB key prompt: shown once on a fresh install (keep the built-in key or
// enter an own one) and whenever TMDB rejects the key in use.
import { otter } from './api';
import type { TmdbKeyStatus } from '@neoworks-dev/otter-sdk/client';

export const TMDB_SIGNUP_URL = 'https://www.themoviedb.org/signup';
export const TMDB_API_SETTINGS_URL = 'https://www.themoviedb.org/settings/api';

export interface TmdbKeyState {
	status: TmdbKeyStatus;
	usesDefaultKey: boolean;
	promptSeen: boolean;
}

/** Why the prompt shows: "welcome" on first run, "broken" when no key works. */
export type TmdbKeyPrompt = 'welcome' | 'broken' | null;

export async function fetchTmdbKeyState(): Promise<TmdbKeyState | null> {
	try {
		const data = await otter.query({
			settings: { tmdbKeyStatus: true, usesDefaultTmdbKey: true, tmdbKeyPromptSeen: true }
		});
		return {
			status: data.settings.tmdbKeyStatus,
			usesDefaultKey: data.settings.usesDefaultTmdbKey,
			promptSeen: data.settings.tmdbKeyPromptSeen
		};
	} catch {
		return null;
	}
}

export function promptFor(state: TmdbKeyState | null): TmdbKeyPrompt {
	if (!state) return null;
	if (state.status !== 'OK') return 'broken';
	if (!state.promptSeen) return 'welcome';
	return null;
}

/** Whether TMDB accepts key; throws when TMDB can't be reached. */
export async function checkTmdbKey(key: string): Promise<boolean> {
	const data = await otter.query({ checkTmdbKey: { __args: { key } } });
	return data.checkTmdbKey;
}

/** Saves an own key (empty switches back to the built-in one) and closes the prompt. */
export async function saveTmdbKey(key: string): Promise<void> {
	await otter.mutation({
		updateSettings: {
			__args: { input: { tmdbApiKey: key.trim(), tmdbKeyPromptSeen: true } },
			tmdbApiKey: true
		}
	});
}

/** Keeps the built-in key and stops the first-run prompt. */
export async function dismissTmdbKeyPrompt(): Promise<void> {
	await otter.mutation({
		updateSettings: { __args: { input: { tmdbKeyPromptSeen: true } }, tmdbKeyPromptSeen: true }
	});
}
