import { describe, expect, test } from 'bun:test';
import {
	expectedPosition,
	formatRoomCode,
	inviteText,
	needsSeek,
	catchUpRate,
	otherPeers,
	parsePayload,
	peersNotReady,
	roomCodeFromText,
	type TogetherRoom
} from '../src/together';

describe('roomCodeFromText', () => {
	test('reads typed codes, links and invite texts', () => {
		expect(roomCodeFromText('k7qd-9mza')).toBe('K7QD9MZA');
		expect(roomCodeFromText('K7QD 9MZA')).toBe('K7QD9MZA');
		expect(roomCodeFromText('otter://together/K7QD9MZA')).toBe('K7QD9MZA');
		expect(roomCodeFromText(inviteText('K7QD9MZA'))).toBe('K7QD9MZA');
	});

	test('rejects text without a code', () => {
		expect(roomCodeFromText('hello')).toBeNull();
		expect(roomCodeFromText('O0I1-O0I1')).toBeNull();
	});
});

test('formatRoomCode splits the code in halves', () => {
	expect(formatRoomCode('K7QD9MZA')).toBe('K7QD-9MZA');
});

describe('parsePayload', () => {
	test('accepts known payloads', () => {
		expect(parsePayload('{"type":"chat","text":"hi"}')).toEqual({ type: 'chat', text: 'hi' });
	});

	test('drops unknown or malformed payloads', () => {
		expect(parsePayload('{"type":"unknown"}')).toBeNull();
		expect(parsePayload('not json')).toBeNull();
		expect(parsePayload('null')).toBeNull();
	});
});

describe('expectedPosition', () => {
	const playback = {
		type: 'playback' as const,
		mediaId: 'movie:1',
		position: 100,
		playing: true,
		heartbeat: false
	};

	test('advances while playing', () => {
		expect(expectedPosition(playback, 1000, 3000)).toBe(102);
	});

	test('stays put while paused', () => {
		expect(expectedPosition({ ...playback, playing: false }, 1000, 3000)).toBe(100);
	});
});

test('needsSeek jumps only past one second of drift', () => {
	expect(needsSeek(100, 100.9)).toBe(false);
	expect(needsSeek(100, 101.2)).toBe(true);
	expect(needsSeek(101.2, 100)).toBe(true);
});

test('catchUpRate nudges small drift', () => {
	expect(catchUpRate(100, 100.1)).toBe(1);
	expect(catchUpRate(100, 100.5)).toBeGreaterThan(1);
	expect(catchUpRate(100.5, 100)).toBeLessThan(1);
});

test('otherPeers leaves out self', () => {
	const self = { id: 'a', name: 'Ana', avatar: null };
	const room: TogetherRoom = {
		code: 'K7QD9MZA',
		hosting: true,
		status: 'CONNECTED',
		self,
		peers: [self, { id: 'b', name: 'Ben', avatar: null }]
	};
	expect(otherPeers(room).map((peer) => peer.name)).toEqual(['Ben']);
	expect(otherPeers(null)).toEqual([]);
});

test('peersNotReady waits for peers elsewhere, unknown or loading', () => {
	const peers = ['ready', 'loading', 'elsewhere', 'unknown'].map((id) => ({
		id,
		name: id,
		avatar: null
	}));
	const states = {
		ready: { mediaId: 'movie:1', buffering: false },
		loading: { mediaId: 'movie:1', buffering: true },
		elsewhere: { mediaId: 'movie:2', buffering: false }
	};
	expect(peersNotReady(peers, states, 'movie:1').map((peer) => peer.id)).toEqual([
		'loading',
		'elsewhere',
		'unknown'
	]);
});
