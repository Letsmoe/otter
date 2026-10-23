// Stream links of a movie or episode: live lookup over WebSocket, forced
// re-lookup, and resolving a stored link into a playable URL.
import { otter, getWsClient } from './api';
import { languageRank, preferredLanguages } from './languages';
import type { Stream as GeneratedStream } from '@neoworks-dev/otter-sdk/client';

export type Stream = Omit<GeneratedStream, '__typename'>;

export interface StreamUpdate {
	streams: Stream[];
	// True on the last update of a lookup.
	done: boolean;
}

export interface PlayableStream {
	url: string;
	mimeType: string;
	quality: string | null;
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

const STREAMS_SUBSCRIPTION = `
	subscription Streams($id: ID!) {
		streams(id: $id) {
			done
			streams { id plugin url label quality language format sizeBytes speedBps playable foundAt }
		}
	}
`;

// Known-playable links first, untested next, known-broken last.
function playableRank(stream: Stream): number {
	if (stream.playable === true) return 0;
	if (stream.playable === false) return 2;
	return 1;
}

// Known-broken links last; otherwise the profile's languages first, then
// known-playable before untested.
export function rankStreams(streams: Stream[], preferred: string[] = preferredLanguages()): Stream[] {
	return [...streams].sort((first, second) => compareStreams(first, second, preferred));
}

function compareStreams(first: Stream, second: Stream, preferred: string[]): number {
	const broken = Number(first.playable === false) - Number(second.playable === false);
	if (broken !== 0) return broken;
	const language = languageRank(first.language, preferred) - languageRank(second.language, preferred);
	if (language !== 0) return language;
	return playableRank(first) - playableRank(second);
}

/**
 * Live stream list of a movie or episode. The backend sends the stored list
 * first, then updates as plugins answer. Returns an unsubscribe function; a
 * no-op during SSR.
 */
export function subscribeStreams(
	mediaId: string,
	onUpdate: (update: StreamUpdate) => void
): () => void {
	const client = getWsClient();
	if (!client) return () => {};

	return client.subscribe(
		{ query: STREAMS_SUBSCRIPTION, variables: { id: mediaId } },
		{
			next(message) {
				const update = message.data?.streams as StreamUpdate | undefined;
				if (update) onUpdate(update);
			},
			error(subscriptionError) {
				console.error('streams subscription error', subscriptionError);
				onUpdate({ streams: [], done: true });
			},
			complete() {}
		}
	);
}

/** Ask every streams plugin again; results arrive through subscribeStreams. */
export async function findStreams(mediaId: string): Promise<void> {
	await otter.mutation({ findStreams: { __args: { id: mediaId } } });
}

/** Stored links of a movie without starting a lookup. */
export async function fetchStoredStreams(mediaId: string): Promise<Stream[]> {
	try {
		const data = await otter.query({ title: { __args: { id: mediaId }, streams: STREAM_SELECT } });
		if (!data.title) return [];
		return rankStreams(data.title.streams);
	} catch {
		return [];
	}
}

export async function resolveStream(streamId: string): Promise<PlayableStream> {
	const data = await otter.mutation({
		resolveStream: { __args: { streamId }, url: true, mimeType: true, quality: true }
	});
	return data.resolveStream;
}
