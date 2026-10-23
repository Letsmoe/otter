// Stream lookup and playback for one movie or episode, shared by the movie and
// series pages: the player opens at once, then plays the best link as soon as
// one arrives and falls back to the next ones when a link is dead.
import {
	findStreams,
	rankStreams,
	resolveStream,
	subscribeStreams,
	type Stream
} from '@neoworks-dev/otter-app/streams';
import { adoptSharedSource, type SharedSource } from '@neoworks-dev/otter-app/together';

// How many sources Play tries before giving up.
const PLAY_ATTEMPTS = 3;

export interface PlayingSource {
	src: string;
	mimeType: string;
	sourceId: string;
}

export class StreamPlayback {
	streams = $state<Stream[]>([]);
	searching = $state(true);
	loadingId = $state<string | null>(null);
	playerOpen = $state(false);
	playing = $state<PlayingSource | null>(null);
	error = $state<string | null>(null);

	#mediaId: string | null = null;
	#autoplayAttempted = false;
	#unsubscribe: (() => void) | null = null;

	/** Follows the links of one title; stored links come first, then plugin answers. */
	follow(mediaId: string) {
		this.stop();
		this.#mediaId = mediaId;
		this.streams = [];
		this.searching = true;
		this.#unsubscribe = subscribeStreams(mediaId, (update) => {
			this.streams = rankStreams(update.streams);
			this.searching = !update.done;
			this.#autoplay();
		});
	}

	stop() {
		this.#unsubscribe?.();
		this.#unsubscribe = null;
	}

	/** Opens the player; playback starts once a link is there. */
	play() {
		this.playerOpen = true;
		this.playing = null;
		this.error = null;
		this.#autoplayAttempted = false;
		this.#autoplay();
	}

	close() {
		this.playerOpen = false;
		this.playing = null;
	}

	/**
	 * Watch Together: plays the link someone else plays (stored here first),
	 * falling back to this device's own best links when it can't be resolved.
	 */
	async playShared(source: SharedSource | null) {
		if (!source || !this.#mediaId) {
			this.play();
			return;
		}
		this.playerOpen = true;
		this.playing = null;
		this.error = null;
		this.#autoplayAttempted = true;
		if (await this.#tryShared(this.#mediaId, source)) return;
		this.#autoplayAttempted = false;
		this.#autoplay();
	}

	async #tryShared(mediaId: string, source: SharedSource): Promise<boolean> {
		try {
			const stream = await adoptSharedSource(mediaId, source);
			if (!this.streams.some((candidate) => candidate.id === stream.id)) {
				this.streams = [stream, ...this.streams];
			}
			return await this.#tryPlay(stream);
		} catch {
			return false;
		}
	}

	/** The playing link, shared with the others in Watch Together. */
	get playingSource(): SharedSource | null {
		const sourceId = this.playing?.sourceId;
		const stream = this.streams.find((candidate) => candidate.id === sourceId);
		if (!stream) return null;
		return { url: stream.url, plugin: stream.plugin, label: stream.label || '' };
	}

	/** Opens the player on one chosen link. */
	playSource(stream: Stream) {
		this.play();
		this.#autoplayAttempted = true;
		this.switchSource(stream.id);
	}

	/** Switching from inside the player; the player keeps its position. */
	async switchSource(sourceId: string) {
		const stream = this.streams.find((candidate) => candidate.id === sourceId);
		if (!stream) return;
		this.error = null;
		if (!(await this.#tryPlay(stream))) this.error = 'This source could not be played.';
	}

	async searchAgain(): Promise<boolean> {
		if (!this.#mediaId || this.searching) return true;
		this.searching = true;
		try {
			await findStreams(this.#mediaId);
			return true;
		} catch {
			this.searching = false;
			return false;
		}
	}

	/** Sources still being searched before the first one arrived. */
	get busy(): boolean {
		return this.streams.length === 0 && this.searching;
	}

	get nothingFound(): boolean {
		return this.streams.length === 0 && !this.searching;
	}

	get status(): string {
		if (this.loadingId) return 'Loading…';
		return 'Finding sources…';
	}

	get sourceOptions(): { id: string; label: string; plugin: string }[] {
		return this.streams.map((stream) => ({
			id: stream.id,
			label: sourceLabel(stream),
			plugin: stream.plugin
		}));
	}

	#autoplay() {
		if (!this.playerOpen || this.playing || this.#autoplayAttempted) return;
		if (this.streams.length > 0) {
			this.#playBest();
			return;
		}
		if (!this.searching) this.error = 'No sources found.';
	}

	async #playBest() {
		this.#autoplayAttempted = true;
		for (const stream of this.streams.slice(0, PLAY_ATTEMPTS)) {
			if (!this.playerOpen) return;
			if (await this.#tryPlay(stream)) return;
		}
		this.error = 'No source could be played.';
	}

	async #tryPlay(stream: Stream): Promise<boolean> {
		const mediaId = this.#mediaId;
		this.loadingId = stream.id;
		try {
			const resolved = await resolveStream(stream.id);
			// The player moved on to another title while this link resolved.
			if (mediaId !== this.#mediaId) return true;
			this.playing = {
				src: resolved.url,
				mimeType: resolved.mimeType,
				sourceId: stream.id
			};
			return true;
		} catch {
			return false;
		} finally {
			this.loadingId = null;
		}
	}
}

function sourceLabel(stream: Stream): string {
	const parts = [stream.label || 'Link'];
	if (stream.quality) parts.push(stream.quality);
	if (stream.language) parts.push(stream.language);
	return parts.join(' · ');
}
