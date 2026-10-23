import { writable } from 'svelte/store';
import { avatarImage } from './avatars';
const browser = typeof localStorage !== 'undefined';

export interface Profile {
	id: string;
	name: string;
	avatarUrl: string | null;
	hasPin: boolean;
	isChild: boolean;
	// Missing on profiles stored before languages existed.
	languages?: string[];
	// The caption and sound preferences are missing on profiles stored before they existed.
	subtitlesEnabled?: boolean;
	// ISO 639-1; null means the first audio language.
	subtitleLanguage?: string | null;
	subtitleSize?: 'SMALL' | 'MEDIUM' | 'LARGE';
	voiceBoost?: boolean;
}

// Bumped with the profile shape change so stale stored profiles are dropped.
const STORAGE_KEY = 'otter_active_profile_v2';
const LEGACY_STORAGE_KEY = 'shutterly_active_profile';

function readStoredProfile(): Profile | null {
	if (!browser) return null;
	localStorage.removeItem(LEGACY_STORAGE_KEY);
	const stored = localStorage.getItem(STORAGE_KEY);
	if (!stored) return null;
	return JSON.parse(stored) as Profile;
}

function createProfileStore() {
	const { subscribe, set, update } = writable<Profile | null>(readStoredProfile());

	return {
		subscribe,
		login(profile: Profile) {
			if (browser) localStorage.setItem(STORAGE_KEY, JSON.stringify(profile));
			set(profile);
		},
		// Applies changes to the signed-in profile, e.g. after the player saved a preference.
		update(changes: Partial<Profile>) {
			update((current) => {
				if (!current) return current;
				const updated = { ...current, ...changes };
				if (browser) localStorage.setItem(STORAGE_KEY, JSON.stringify(updated));
				return updated;
			});
		},
		logout() {
			if (browser) localStorage.removeItem(STORAGE_KEY);
			set(null);
		}
	};
}

export const activeProfile = createProfileStore();

export function avatarFor(profile: Profile): string {
	return avatarImage(profile.avatarUrl, profile.name);
}
