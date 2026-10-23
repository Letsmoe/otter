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
	const { subscribe, set } = writable<Profile | null>(readStoredProfile());

	return {
		subscribe,
		login(profile: Profile) {
			if (browser) localStorage.setItem(STORAGE_KEY, JSON.stringify(profile));
			set(profile);
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
