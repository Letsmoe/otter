// The Watch Together session: room state, chat, reactions and the others'
// playback. The player reports what it plays and what the user does; pages
// open what someone else started (remoteMedia).
import { goto } from '$app/navigation';
import { page } from '$app/state';
import {
	hostTogether,
	isRoomOpen,
	joinTogether,
	leaveTogether,
	otherPeers,
	parsePayload,
	peersNotReady,
	sendTogether,
	subscribeTogether,
	type MediaPayload,
	type PeerPlayerState,
	type PlaybackPayload,
	type TogetherMessage,
	type TogetherPayload,
	type TogetherPeer,
	type TogetherRoom,
	type TogetherUpdate
} from '@neoworks-dev/otter-app/together';
import { notifications } from './notifications';

const CHAT_LIMIT = 50;
const REACTION_MS = 2600;

export interface ChatLine {
	id: number;
	from: TogetherPeer;
	text: string;
	mine: boolean;
}

export interface FloatingReaction {
	id: number;
	emoji: string;
	from: TogetherPeer;
	/** Horizontal offset in percent so simultaneous reactions don't overlap. */
	offset: number;
}

export interface RemotePlayback {
	payload: PlaybackPayload;
	receivedAt: number;
}

/** What the open player tells the session. */
export interface PlayerReport {
	media: MediaPayload;
	playing: () => boolean;
	position: () => number;
}

class TogetherSession {
	room = $state<TogetherRoom | null>(null);
	chat = $state<ChatLine[]>([]);
	reactions = $state<FloatingReaction[]>([]);
	/** Each peer's player: what it shows and whether it's still loading. */
	peerStates = $state<Record<string, PeerPlayerState>>({});
	/** Title whose playback starts even though not everyone is ready. */
	holdOverrideMediaId = $state<string | null>(null);
	/** Media someone else started that this device hasn't opened yet. */
	remoteMedia = $state<MediaPayload | null>(null);
	remotePlayback = $state<RemotePlayback | null>(null);
	busy = $state(false);
	/** The Watch Together sheet over the app. */
	sheetOpen = $state(false);
	/** Code from an invite link, prefilled in the sheet. */
	inviteCode = $state('');

	#unsubscribe: (() => void) | null = null;
	#player: PlayerReport | null = null;
	#knownPeerIds = new Set<string>();
	// What the room watches as far as this device knows; announcing it again
	// would make everyone reopen it.
	#roomMediaId: string | null = null;
	#ownState: PeerPlayerState = { mediaId: null, buffering: false };
	#counter = 0;

	get open(): boolean {
		return isRoomOpen(this.room);
	}

	get connected(): boolean {
		return this.room?.status === 'CONNECTED';
	}

	get others(): TogetherPeer[] {
		return otherPeers(this.room);
	}

	openSheet(inviteCode = '') {
		this.inviteCode = inviteCode;
		this.sheetOpen = true;
	}

	closeSheet() {
		this.sheetOpen = false;
	}

	/** Follows the backend's room for the app's lifetime. */
	start() {
		if (this.#unsubscribe) return;
		this.#unsubscribe = subscribeTogether((update) => this.#onUpdate(update));
	}

	async host(profileId: string): Promise<boolean> {
		return this.#enter(() => hostTogether(profileId));
	}

	async join(profileId: string, code: string): Promise<boolean> {
		return this.#enter(() => joinTogether(profileId, code));
	}

	async leave() {
		this.#reset();
		this.room = null;
		await leaveTogether().catch(() => {});
	}

	async #enter(open: () => Promise<TogetherRoom>): Promise<boolean> {
		this.busy = true;
		this.#reset();
		try {
			this.room = await open();
			this.#knownPeerIds = new Set(this.room.peers.map((peer) => peer.id));
			return true;
		} catch {
			notifications.push('Could not open Watch Together', 'error');
			return false;
		} finally {
			this.busy = false;
		}
	}

	#reset() {
		this.chat = [];
		this.reactions = [];
		this.peerStates = {};
		this.holdOverrideMediaId = null;
		this.remoteMedia = null;
		this.remotePlayback = null;
		this.#knownPeerIds = new Set();
		this.#roomMediaId = null;
	}

	// Player -----------------------------------------------------------------

	/** The player opened or switched title; the others follow. */
	attachPlayer(report: PlayerReport) {
		this.#player = report;
		if (!this.connected || this.#roomMediaId === report.media.mediaId) return;
		this.#roomMediaId = report.media.mediaId;
		this.#send(report.media);
	}

	detachPlayer(mediaId: string) {
		if (this.#player?.media.mediaId !== mediaId) return;
		this.#player = null;
		this.announcePlayerState(null, false);
	}

	/** The user pressed play, pause or seeked. */
	announcePlayback(heartbeat = false) {
		const player = this.#player;
		if (!player || !this.connected) return;
		this.#send({
			type: 'playback',
			mediaId: player.media.mediaId,
			playing: player.playing(),
			position: player.position(),
			heartbeat
		});
	}

	/** This player's title and whether it is still loading; the others wait for it. */
	announcePlayerState(mediaId: string | null, buffering: boolean) {
		this.#ownState = { mediaId, buffering };
		if (!this.connected) return;
		this.#send({ type: 'status', mediaId, buffering });
	}

	/** Others who haven't loaded this title yet; playback holds until they have. */
	holdingFor(mediaId: string | undefined): TogetherPeer[] {
		if (!mediaId || !this.connected || this.holdOverrideMediaId === mediaId) return [];
		return peersNotReady(this.others, this.peerStates, mediaId);
	}

	startWithoutWaiting(mediaId: string) {
		this.holdOverrideMediaId = mediaId;
	}

	isBuffering(peerId: string): boolean {
		return this.peerStates[peerId]?.buffering === true;
	}

	sendChat(text: string) {
		const trimmed = text.trim();
		if (!trimmed || !this.room) return;
		this.#addChat(this.room.self, trimmed, true);
		this.#send({ type: 'chat', text: trimmed });
	}

	sendReaction(emoji: string) {
		if (!this.room) return;
		this.#addReaction(this.room.self, emoji);
		this.#send({ type: 'reaction', emoji });
	}

	/** A page takes the media it was opened for, so it plays only once. */
	takeRemoteMedia(mediaId: string): MediaPayload | null {
		const pending = this.remoteMedia;
		if (!pending || pending.mediaId !== mediaId) return null;
		this.remoteMedia = null;
		return pending;
	}

	#send(payload: TogetherPayload) {
		sendTogether(payload);
	}

	// Incoming ---------------------------------------------------------------

	#onUpdate(update: TogetherUpdate) {
		const previous = this.room;
		this.room = update.room;
		this.#announceRoomChanges(previous, update.room);
		if (update.message) this.#onMessage(update.message);
	}

	#announceRoomChanges(previous: TogetherRoom | null, current: TogetherRoom | null) {
		if (!current) return;
		if (previous?.status !== current.status) this.#announceStatus(current);
		const joined = current.peers.filter((peer) => !this.#knownPeerIds.has(peer.id));
		const left = peersMissingFrom(previous, current);
		this.#knownPeerIds = new Set(current.peers.map((peer) => peer.id));
		for (const peer of joined) notifications.push(`${peer.name} joined Watch Together`, 'success');
		for (const peer of left) notifications.push(`${peer.name} left Watch Together`);
		if (joined.length > 0) this.#catchUp();
	}

	#announceStatus(room: TogetherRoom) {
		if (room.status === 'CLOSED') notifications.push('Watch Together ended', 'info');
		if (room.status === 'FAILED') notifications.push('Could not reach the host', 'error');
	}

	// Someone new joined: tell them what is playing and where.
	#catchUp() {
		if (!this.connected) return;
		this.#send({ type: 'status', ...this.#ownState });
		if (!this.#player) return;
		this.#roomMediaId = this.#player.media.mediaId;
		this.#send(this.#player.media);
		this.announcePlayback(true);
	}

	#onMessage(message: TogetherMessage) {
		const payload = parsePayload(message.payload);
		if (!payload) return;
		if (payload.type === 'media') this.#onMedia(payload);
		if (payload.type === 'playback') this.remotePlayback = { payload, receivedAt: Date.now() };
		if (payload.type === 'status') this.#onPeerState(message.from, payload);
		if (payload.type === 'chat') this.#onChat(message.from, payload.text);
		if (payload.type === 'reaction') this.#addReaction(message.from, payload.emoji);
	}

	#onPeerState(from: TogetherPeer, state: PeerPlayerState) {
		this.peerStates = {
			...this.peerStates,
			[from.id]: { mediaId: state.mediaId, buffering: state.buffering }
		};
	}

	#onMedia(media: MediaPayload) {
		if (this.#player?.media.mediaId === media.mediaId) return;
		this.remoteMedia = media;
		this.remotePlayback = null;
		this.#roomMediaId = media.mediaId;
		if (`${page.url.pathname}${page.url.search}` !== media.route) goto(media.route);
	}

	#onChat(from: TogetherPeer, text: string) {
		this.#addChat(from, text, false);
		// The player shows chat itself.
		if (!this.#player) notifications.push(`${from.name}: ${text}`);
	}

	#addChat(from: TogetherPeer, text: string, mine: boolean) {
		this.#counter += 1;
		this.chat = [...this.chat, { id: this.#counter, from, text, mine }].slice(-CHAT_LIMIT);
	}

	#addReaction(from: TogetherPeer, emoji: string) {
		this.#counter += 1;
		const id = this.#counter;
		const offset = Math.round(Math.random() * 60);
		this.reactions = [...this.reactions, { id, emoji, from, offset }];
		setTimeout(() => {
			this.reactions = this.reactions.filter((reaction) => reaction.id !== id);
		}, REACTION_MS);
	}
}

export const together = new TogetherSession();

function peersMissingFrom(previous: TogetherRoom | null, current: TogetherRoom): TogetherPeer[] {
	if (!previous) return [];
	const currentIds = new Set(current.peers.map((peer) => peer.id));
	return previous.peers.filter((peer) => !currentIds.has(peer.id));
}
