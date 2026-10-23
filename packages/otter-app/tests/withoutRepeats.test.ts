import { expect, test } from 'bun:test';
import { withoutRepeats, type RailItem } from '../src/discovery';

function item(id: string): RailItem {
	return { id, title: id, posterUrl: null, mediaType: 'movie', backdropUrl: null };
}

test('a title only stays in the first rail that has it', () => {
	const rails = withoutRepeats([[item('a'), item('b')], [item('b'), item('c')], [item('a')]]);
	expect(rails.map((rail) => rail.map((entry) => entry.id))).toEqual([['a', 'b'], ['c'], []]);
});

test('titles shown above the rails are skipped', () => {
	const rails = withoutRepeats([[item('featured'), item('x')]], ['featured']);
	expect(rails[0].map((entry) => entry.id)).toEqual(['x']);
});

test('the same name and year under another id counts as a repeat', () => {
	const first = { ...item('movie:1'), title: 'Rocketman', subtitle: '2019' };
	const copy = { ...item('movie:2'), title: 'Rocketman', subtitle: '2019' };
	const remake = { ...item('movie:3'), title: 'Rocketman', subtitle: '1997' };
	const rails = withoutRepeats([[first, copy, remake]]);
	expect(rails[0].map((entry) => entry.id)).toEqual(['movie:1', 'movie:3']);
});
