import { describe, expect, test } from 'bun:test';
import {
	DEFAULT_CAPTION_PREFERENCES,
	captionLanguageOf,
	captionPreferencesOf,
	defaultCaptionKey,
	embeddedTrackLabel,
	twoLetterLanguage,
	type CaptionPreferences,
	type CaptionTrack
} from '../src/captions';
import type { Profile } from '../src/profile';

function profile(changes: Partial<Profile>): Profile {
	return { id: 'profile:1', name: 'Frank', avatarUrl: null, hasPin: false, isChild: false, ...changes };
}

function track(key: string, kind: CaptionTrack['kind'], language: string | null): CaptionTrack {
	return { key, kind, language, label: key, detail: null };
}

const enabled: CaptionPreferences = { ...DEFAULT_CAPTION_PREFERENCES, subtitlesEnabled: true };

describe('captionPreferencesOf', () => {
	test('profiles stored before captions existed get the defaults', () => {
		expect(captionPreferencesOf(profile({}))).toEqual(DEFAULT_CAPTION_PREFERENCES);
		expect(captionPreferencesOf(null)).toEqual(DEFAULT_CAPTION_PREFERENCES);
	});

	test('keeps what the profile stored', () => {
		const stored = profile({ subtitlesEnabled: true, subtitleLanguage: 'en', subtitleSize: 'LARGE', voiceBoost: true });
		expect(captionPreferencesOf(stored)).toEqual({
			subtitlesEnabled: true,
			subtitleLanguage: 'en',
			subtitleSize: 'LARGE',
			voiceBoost: true
		});
	});
});

describe('captionLanguageOf', () => {
	test('the chosen language wins, then the first audio language', () => {
		expect(captionLanguageOf(profile({ subtitleLanguage: 'fr', languages: ['de'] }))).toBe('fr');
		expect(captionLanguageOf(profile({ subtitleLanguage: null, languages: ['it', 'de'] }))).toBe('it');
	});

	test('falls back to the default audio language', () => {
		expect(captionLanguageOf(profile({}))).toBe('de');
		expect(captionLanguageOf(null)).toBe('de');
	});
});

describe('twoLetterLanguage', () => {
	test('reduces region and three-letter codes', () => {
		expect(twoLetterLanguage('de-DE')).toBe('de');
		expect(twoLetterLanguage('en_US')).toBe('en');
		expect(twoLetterLanguage('deu')).toBe('de');
		expect(twoLetterLanguage('ENG')).toBe('en');
	});

	test('unknown or missing codes are null', () => {
		expect(twoLetterLanguage('xyz')).toBeNull();
		expect(twoLetterLanguage(undefined)).toBeNull();
		expect(twoLetterLanguage('')).toBeNull();
	});
});

describe('defaultCaptionKey', () => {
	const tracks = [track('embedded:0', 'embedded', 'de'), track('external:a', 'external', 'en'), track('external:b', 'external', 'de')];

	test('captions stay off unless the profile turned them on', () => {
		expect(defaultCaptionKey(tracks, DEFAULT_CAPTION_PREFERENCES, 'de')).toBeNull();
	});

	test('a file of the caption language beats an embedded track', () => {
		expect(defaultCaptionKey(tracks, enabled, 'de')).toBe('external:b');
		expect(defaultCaptionKey(tracks, enabled, 'en')).toBe('external:a');
	});

	test('uses an embedded track when no file matches', () => {
		const embeddedOnly = [track('embedded:0', 'embedded', 'de')];
		expect(defaultCaptionKey(embeddedOnly, enabled, 'de')).toBe('embedded:0');
	});

	test('stays off when nothing matches the language', () => {
		expect(defaultCaptionKey(tracks, enabled, 'fr')).toBeNull();
		expect(defaultCaptionKey([], enabled, 'de')).toBeNull();
	});
});

describe('embeddedTrackLabel', () => {
	test('prefers the name, then the language, then a number', () => {
		expect(embeddedTrackLabel('Deutsch', 'de', 0)).toBe('Deutsch');
		expect(embeddedTrackLabel(undefined, 'de', 0)).toBe('de');
		expect(embeddedTrackLabel(undefined, undefined, 1)).toBe('Track 2');
	});
});
