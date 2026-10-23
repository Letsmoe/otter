// Watch Together: rooms run in the backend (Nostr signaling + WebRTC); this
// module talks to them and defines what peers send each other.
import { otter, getWsClient } from './api';
import type { Stream } from './streams';

export type TogetherStatus = 'WAITING' | 'CONNECTING' | 'CONNECTED' | 'CLOSED' | 'FAILED';

export interface TogetherPeer {
	id: string;
	name: string;
	avatar: string | null;
}

export interface TogetherRoom {
	code: string;
	hosting: boolean;
	status: TogetherStatus;
	self: TogetherPeer;
	peers: TogetherPeer[];
}

export interface TogetherMessage {
	from: TogetherPeer;
	payload: string;
}

export interface TogetherUpdate {
	room: TogetherRoom | null;
	message: TogetherMessage | null;
}

/** The original link someone plays; the others store and resolve it themselves. */
export interface SharedSource {
	url: string;
	plugin: string;
	label: string;
}

/** What is playing: lets the others open the same title and source. */
export interface MediaPayload {
	type: 'media';
	mediaId: string;
	/** App path that plays the title, e.g. /movies/movie:603. */
	route: string;
	title: string;
	source: SharedSource | null;
}

export interface PlaybackPayload {
	type: 'playback';
	mediaId: string;
	playing: boolean;
	position: number;
	/** Periodic sync from the host rather than someone pressing a control. */
	heartbeat: boolean;
}

export interface StatusPayload {
	type: 'status';
	mediaId: string | null;
	buffering: boolean;
}

export interface ChatPayload {
	type: 'chat';
	text: string;
}

export interface ReactionPayload {
	type: 'reaction';
	emoji: string;
}

export type TogetherPayload =
	| MediaPayload
	| PlaybackPayload
	| StatusPayload
	| ChatPayload
	| ReactionPayload;

const PEER_SELECT = { id: true, name: true, avatar: true } as const;

const ROOM_SELECT = {
	code: true,
	hosting: true,
	status: true,
	self: PEER_SELECT,
	peers: PEER_SELECT
} as const;

const TOGETHER_SUBSCRIPTION = `
	subscription Together {
		together {
			room { code hosting status self { id name avatar } peers { id name avatar } }
			message { from { id name avatar } payload }
		}
	}
`;

export const INVITE_LINK_PREFIX = 'otter://together/';

const CODE_PATTERN = /([A-HJ-NP-Z2-9]{4})[-\s]?([A-HJ-NP-Z2-9]{4})/i;

/** Live room state and messages; returns an unsubscribe function. */
export function subscribeTogether(onUpdate: (update: TogetherUpdate) => void): () => void {
	const client = getWsClient();
	if (!client) return () => {};
	return client.subscribe(
		{ query: TOGETHER_SUBSCRIPTION },
		{
			next(message) {
				const update = message.data?.together as TogetherUpdate | undefined;
				if (update) onUpdate(update);
			},
			error(subscriptionError) {
				console.error('together subscription error', subscriptionError);
			},
			complete() {}
		}
	);
}

export async function hostTogether(profileId: string): Promise<TogetherRoom> {
	const data = await otter.mutation({ hostTogether: { __args: { profileId }, ...ROOM_SELECT } });
	return data.hostTogether as TogetherRoom;
}

export async function joinTogether(profileId: string, code: string): Promise<TogetherRoom> {
	const data = await otter.mutation({
		joinTogether: { __args: { profileId, code }, ...ROOM_SELECT }
	});
	return data.joinTogether as TogetherRoom;
}

export async function leaveTogether(): Promise<void> {
	await otter.mutation({ leaveTogether: true });
}

export async function sendTogether(payload: TogetherPayload): Promise<boolean> {
	try {
		await otter.mutation({ sendTogether: { __args: { payload: JSON.stringify(payload) } } });
		return true;
	} catch {
		return false;
	}
}

const STREAM_SELECT = {
	id: true,
	plugin: true,
	url: true,
	label: true,
	quality: true,
	language: true,
	format: true,
	sizeBytes: true,
	speedBps: true,
	playable: true,
	foundAt: true
} as const;

/** Stores someone else's link for this title in the local backend. */
export async function adoptSharedSource(mediaId: string, source: SharedSource): Promise<Stream> {
	const data = await otter.mutation({
		adoptStream: {
			__args: { mediaId, url: source.url, plugin: source.plugin, label: source.label },
			...STREAM_SELECT
		}
	});
	return data.adoptStream;
}

/** Parses a received payload; unknown or malformed ones are null. */
export function parsePayload(text: string): TogetherPayload | null {
	let parsed: unknown;
	try {
		parsed = JSON.parse(text);
	} catch {
		return null;
	}
	if (!isPayload(parsed)) return null;
	return parsed;
}

function isPayload(value: unknown): value is TogetherPayload {
	if (typeof value !== 'object' || value === null) return false;
	const type = (value as { type?: unknown }).type;
	return ['media', 'playback', 'status', 'chat', 'reaction'].includes(type as string);
}

/** "K7QD9MZA" → "K7QD-9MZA". */
export function formatRoomCode(code: string): string {
	return `${code.slice(0, 4)}-${code.slice(4)}`;
}

/** Finds a room code in a typed code, an invite link or a pasted invite text. */
export function roomCodeFromText(text: string): string | null {
	const match = CODE_PATTERN.exec(text);
	if (!match) return null;
	return `${match[1]}${match[2]}`.toUpperCase();
}

export function inviteLink(code: string): string {
	return `${INVITE_LINK_PREFIX}${code}`;
}

export function inviteText(code: string): string {
	return `Watch with me on Otter! Room code ${formatRoomCode(code)} — or open ${inviteLink(code)}`;
}

/** Peers still in the room: hosting alone, connecting or connected. */
export function isRoomOpen(room: TogetherRoom | null): boolean {
	if (!room) return false;
	return room.status === 'WAITING' || room.status === 'CONNECTING' || room.status === 'CONNECTED';
}

export function otherPeers(room: TogetherRoom | null): TogetherPeer[] {
	if (!room) return [];
	return room.peers.filter((peer) => peer.id !== room.self.id);
}

/** What a peer's player last reported. */
export interface PeerPlayerState {
	mediaId: string | null;
	buffering: boolean;
}

/**
 * Peers who would fall behind if playback started: not on this title yet
 * (still opening it, or never reported) or still loading it.
 */
export function peersNotReady(
	peers: TogetherPeer[],
	states: Record<string, PeerPlayerState>,
	mediaId: string
): TogetherPeer[] {
	return peers.filter((peer) => {
		const state = states[peer.id];
		if (!state || state.mediaId !== mediaId) return true;
		return state.buffering;
	});
}

/** Where remote playback is now, given when its message arrived. */
export function expectedPosition(
	playback: PlaybackPayload,
	receivedAt: number,
	now: number
): number {
	if (!playback.playing) return playback.position;
	return playback.position + (now - receivedAt) / 1000;
}

// Beyond this much drift the player jumps; below it, it catches up by
// playing slightly faster or slower (inaudible, no rebuffering).
const SEEK_DRIFT_SECONDS = 1;
// Drift small enough to ignore entirely.
const IN_SYNC_SECONDS = 0.2;
// Playback-rate change used to catch up on small drift.
const CATCH_UP_RATE = 0.05;

export function needsSeek(localPosition: number, remotePosition: number): boolean {
	return Math.abs(localPosition - remotePosition) > SEEK_DRIFT_SECONDS;
}

/** Playback-rate factor that closes small drift: >1 when behind, <1 when ahead. */
export function catchUpRate(localPosition: number, remotePosition: number): number {
	const behindBy = remotePosition - localPosition;
	if (Math.abs(behindBy) <= IN_SYNC_SECONDS) return 1;
	if (behindBy > 0) return 1 + CATCH_UP_RATE;
	return 1 - CATCH_UP_RATE;
}
