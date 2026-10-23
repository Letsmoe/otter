import { describe, expect, test } from 'bun:test';
import { sortRailItems, type RailItem } from '../src/discovery';

function item(title: string, year: number | null): RailItem {
	return { id: title, title, mediaType: 'movie', posterUrl: null, backdropUrl: null, year };
}

const saved = [item('Heat', 1995), item('Alien', 1979), item('Dune', 2021), item('Unknown', null)];

describe('sortRailItems', () => {
	test('added keeps the saved order', () => {
		expect(sortRailItems(saved, 'added').map((entry) => entry.title)).toEqual([
			'Heat',
			'Alien',
			'Dune',
			'Unknown'
		]);
	});

	test('title sorts A–Z', () => {
		expect(sortRailItems(saved, 'title').map((entry) => entry.title)).toEqual([
			'Alien',
			'Dune',
			'Heat',
			'Unknown'
		]);
	});

	test('release puts the newest first and unknown years last', () => {
		expect(sortRailItems(saved, 'release').map((entry) => entry.title)).toEqual([
			'Dune',
			'Heat',
			'Alien',
			'Unknown'
		]);
	});
});
