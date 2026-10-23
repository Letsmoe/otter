import { describe, expect, test } from 'bun:test';
import { existsSync } from 'node:fs';
import { PRESET_AVATARS, avatarImage, defaultAvatarFor } from '../src/avatars';

describe('avatarImage', () => {
	test('otter presets point at the bundled pictures', () => {
		expect(avatarImage('otter:crown', 'Sam')).toBe('/avatars/otter-crown.webp');
	});

	test('old SVG presets still render', () => {
		expect(avatarImage('avatar:blue-smile', 'Sam')).toStartWith('data:image/svg+xml');
	});

	test('profiles without an avatar get a stable otter', () => {
		expect(PRESET_AVATARS).toContain(defaultAvatarFor('Sam'));
		expect(avatarImage(null, 'Sam')).toBe(avatarImage(null, 'Sam'));
		expect(avatarImage(null, 'Sam')).toStartWith('/avatars/otter-');
	});
});

describe('avatar sets', () => {
	test('every set preset maps to its picture', () => {
		expect(avatarImage('robots:lovestruck', 'Sam')).toBe('/avatars/robots-lovestruck.webp');
		expect(avatarImage('kids:dino', 'Sam')).toBe('/avatars/kids-dino.webp');
	});

	test('unknown names fall back to the default otter', () => {
		expect(avatarImage('robots:nope', 'Sam')).toBe(avatarImage(null, 'Sam'));
	});
});

describe('avatar files', () => {
	test('both apps ship a picture for every preset', () => {
		for (const app of ['mobile', 'web']) {
			for (const avatarId of PRESET_AVATARS) {
				const path = `${import.meta.dir}/../../../apps/${app}/static${avatarImage(avatarId, '')}`;
				expect(existsSync(path)).toBe(true);
			}
		}
	});
});
