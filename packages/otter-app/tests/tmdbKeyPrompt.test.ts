import { describe, expect, test } from 'bun:test';
import { promptFor } from '../src/tmdbKey';

describe('promptFor', () => {
	test('asks for a key when the one in use is rejected or missing', () => {
		expect(promptFor({ status: 'INVALID', usesDefaultKey: true, promptSeen: true })).toBe('broken');
		expect(promptFor({ status: 'MISSING', usesDefaultKey: false, promptSeen: false })).toBe('broken');
	});

	test('welcomes once on a fresh install', () => {
		expect(promptFor({ status: 'OK', usesDefaultKey: true, promptSeen: false })).toBe('welcome');
		expect(promptFor({ status: 'OK', usesDefaultKey: true, promptSeen: true })).toBe(null);
	});

	test('stays quiet when the backend is unreachable', () => {
		expect(promptFor(null)).toBe(null);
	});
});
