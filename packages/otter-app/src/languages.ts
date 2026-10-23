// Audio language preferences of a profile and how they rank stream links.
import { get } from 'svelte/store';
import { activeProfile } from './profile';

// What a profile gets until it picks its own; the backend uses the same default.
export const DEFAULT_LANGUAGES = ['de', 'en'];

export const LANGUAGE_CHOICES = [
	{ code: 'de', name: 'German' },
	{ code: 'en', name: 'English' },
	{ code: 'fr', name: 'French' },
	{ code: 'es', name: 'Spanish' },
	{ code: 'it', name: 'Italian' },
	{ code: 'ja', name: 'Japanese' }
];

// Plugins mostly send codes ("de", "de-sub"); older links stored names.
const LANGUAGE_NAMES: Record<string, string> = {
	deutsch: 'de',
	german: 'de',
	english: 'en',
	englisch: 'en'
};

/** The active profile's audio languages, most preferred first. */
export function preferredLanguages(): string[] {
	const languages = get(activeProfile)?.languages;
	if (!languages || languages.length === 0) return DEFAULT_LANGUAGES;
	return languages;
}

/**
 * Lower is better: each preferred language as a dub, then with subtitles,
 * then links without a language, then languages the profile didn't pick.
 */
export function languageRank(language: string | null | undefined, preferred: string[]): number {
	const unknownRank = preferred.length * 2;
	if (!language || language.trim() === '') return unknownRank;
	const normalized = language.trim().toLowerCase();
	const isSubtitled = normalized.endsWith('-sub');
	const code = baseLanguage(normalized.replace(/-sub$/, ''));
	const position = preferred.indexOf(code);
	if (position === -1) return unknownRank + 1;
	let rank = position * 2;
	if (isSubtitled) rank += 1;
	return rank;
}

function baseLanguage(language: string): string {
	const named = LANGUAGE_NAMES[language];
	if (named) return named;
	return language.split('-')[0];
}
