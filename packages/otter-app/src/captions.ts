// Caption and sound preferences of a profile and how they pick a caption track.
import { DEFAULT_LANGUAGES } from './languages';
import type { Profile } from './profile';

export type SubtitleSize = 'SMALL' | 'MEDIUM' | 'LARGE';

export interface CaptionPreferences {
	subtitlesEnabled: boolean;
	// ISO 639-1; null means the first audio language.
	subtitleLanguage: string | null;
	subtitleSize: SubtitleSize;
	voiceBoost: boolean;
}

export const DEFAULT_CAPTION_PREFERENCES: CaptionPreferences = {
	subtitlesEnabled: false,
	subtitleLanguage: null,
	subtitleSize: 'MEDIUM',
	voiceBoost: false
};

export const SUBTITLE_SIZE_CHOICES: { size: SubtitleSize; name: string }[] = [
	{ size: 'SMALL', name: 'Small' },
	{ size: 'MEDIUM', name: 'Medium' },
	{ size: 'LARGE', name: 'Large' }
];

/** The profile's preferences, with the defaults for ones stored before they existed. */
export function captionPreferencesOf(profile: Profile | null): CaptionPreferences {
	if (!profile) return DEFAULT_CAPTION_PREFERENCES;
	return {
		subtitlesEnabled: profile.subtitlesEnabled === true,
		subtitleLanguage: profile.subtitleLanguage || null,
		subtitleSize: profile.subtitleSize || DEFAULT_CAPTION_PREFERENCES.subtitleSize,
		voiceBoost: profile.voiceBoost === true
	};
}

/** The language captions show in: the chosen one, else the first audio language. */
export function captionLanguageOf(profile: Profile | null): string {
	if (profile?.subtitleLanguage) return profile.subtitleLanguage;
	const languages = profile?.languages;
	if (languages && languages.length > 0) return languages[0];
	return DEFAULT_LANGUAGES[0];
}

const THREE_LETTER_LANGUAGES: Record<string, string> = {
	eng: 'en',
	ger: 'de',
	deu: 'de',
	fre: 'fr',
	fra: 'fr',
	spa: 'es',
	ita: 'it',
	jpn: 'ja'
};

/** "de-DE" and "deu" become "de"; null when the code is missing or unknown. */
export function twoLetterLanguage(code: string | null | undefined): string | null {
	if (!code) return null;
	const base = code.trim().toLowerCase().split(/[-_]/)[0];
	if (base.length === 2) return base;
	return THREE_LETTER_LANGUAGES[base] || null;
}

/** One caption the player can show: a file from the backend or a track inside the stream. */
export interface CaptionTrack {
	key: string;
	kind: 'external' | 'embedded';
	language: string | null;
	label: string;
	// The release a file was made for.
	detail: string | null;
}

/** The track to show when playback starts; null keeps captions off. */
export function defaultCaptionKey(tracks: CaptionTrack[], preferences: CaptionPreferences, language: string): string | null {
	if (!preferences.subtitlesEnabled) return null;
	const external = tracks.find((track) => track.kind === 'external' && track.language === language);
	if (external) return external.key;
	const embedded = tracks.find((track) => track.kind === 'embedded' && track.language === language);
	if (embedded) return embedded.key;
	return null;
}

/** Name of a track inside the stream, which often lacks one. */
export function embeddedTrackLabel(name: string | undefined, language: string | undefined, index: number): string {
	if (name) return name;
	if (language) return language;
	return `Track ${index + 1}`;
}
