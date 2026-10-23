import { describe, expect, test } from 'bun:test';
import { languageRank } from '../src/languages';
import { rankStreams, type Stream } from '../src/streams';

function stream(id: string, language: string | null, playable: boolean | null): Stream {
	return { id, language, playable } as Stream;
}

describe('languageRank', () => {
	const preferred = ['de', 'en'];

	test('dubs before subs, in the profile order', () => {
		expect(languageRank('de', preferred)).toBe(0);
		expect(languageRank('de-sub', preferred)).toBe(1);
		expect(languageRank('en', preferred)).toBe(2);
		expect(languageRank('en-sub', preferred)).toBe(3);
	});

	test('unknown before unwanted, and names count as codes', () => {
		expect(languageRank(null, preferred)).toBe(4);
		expect(languageRank('ja', preferred)).toBe(5);
		expect(languageRank('Deutsch', preferred)).toBe(0);
	});
});

describe('rankStreams', () => {
	test('language beats a known-playable link, broken links stay last', () => {
		const ranked = rankStreams(
			[
				stream('english-playable', 'en', true),
				stream('german-broken', 'de', false),
				stream('german-untried', 'de', null),
				stream('german-playable', 'de', true)
			],
			['de', 'en']
		);
		expect(ranked.map((entry) => entry.id)).toEqual([
			'german-playable',
			'german-untried',
			'english-playable',
			'german-broken'
		]);
	});
});
