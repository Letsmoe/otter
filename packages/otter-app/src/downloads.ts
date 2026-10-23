// Movies and episodes saved for offline playback by the backend.
import { otter } from './api';
import { backdropUrl, posterUrl } from './tmdb';
import type { DownloadStatus } from '@neoworks-dev/otter-sdk/client';

export interface DownloadItem {
	id: string;
	status: DownloadStatus;
	progress: number;
	sizeBytes: number;
	error: string | null;
	/** movie:… or episode:…, the id the player tracks progress under. */
	mediaId: string;
	titleId: string;
	titleName: string;
	imageUrl: string | null;
	posterUrl: string | null;
	episode: {
		seasonNumber: number;
		episodeNumber: number;
		name: string | null;
	} | null;
	url: string | null;
	mimeType: string | null;
}

/** A movie, or all downloaded episodes of one series. */
export interface DownloadGroup {
	titleId: string;
	titleName: string;
	imageUrl: string | null;
	posterUrl: string | null;
	items: DownloadItem[];
}

const DOWNLOAD_SELECT = {
	id: true,
	status: true,
	progress: true,
	sizeBytes: true,
	error: true,
	url: true,
	mimeType: true,
	title: { id: true, title: true, backdropPath: true, posterPath: true },
	episode: { id: true, seasonNumber: true, episodeNumber: true, name: true, stillPath: true }
} as const;

interface FetchedDownload {
	id: string;
	status: DownloadStatus;
	progress: number;
	sizeBytes: number;
	error: string | null;
	url: string | null;
	mimeType: string | null;
	title: { id: string; title: string; backdropPath: string | null; posterPath: string | null };
	episode: {
		id: string;
		seasonNumber: number;
		episodeNumber: number;
		name: string | null;
		stillPath: string | null;
	} | null;
}

function toDownloadItem(download: FetchedDownload): DownloadItem {
	const episode = download.episode;
	let mediaId = download.title.id;
	let imageUrl = backdropUrl(download.title.backdropPath, 'w780');
	if (episode) {
		mediaId = episode.id;
		imageUrl = backdropUrl(episode.stillPath, 'w780') || imageUrl;
	}
	return {
		id: download.id,
		status: download.status,
		progress: download.progress,
		sizeBytes: download.sizeBytes,
		error: download.error,
		mediaId,
		titleId: download.title.id,
		titleName: download.title.title,
		imageUrl,
		posterUrl: posterUrl(download.title.posterPath, 'w342'),
		episode,
		url: download.url,
		mimeType: download.mimeType
	};
}

export async function fetchDownloads(profileId: string): Promise<DownloadItem[]> {
	try {
		const data = await otter.query({ downloads: { __args: { profileId }, ...DOWNLOAD_SELECT } });
		return data.downloads.map(toDownloadItem);
	} catch {
		return [];
	}
}

/** Queues a download; without streamId the backend tries the best links. */
export async function startDownload(
	profileId: string,
	mediaId: string,
	streamId?: string
): Promise<DownloadItem> {
	const data = await otter.mutation({
		startDownload: { __args: { profileId, mediaId, streamId }, ...DOWNLOAD_SELECT }
	});
	return toDownloadItem(data.startDownload);
}

export async function deleteDownload(id: string): Promise<void> {
	await otter.mutation({ deleteDownload: { __args: { id } } });
}

/** Where the backend saves finished downloads as named files; null when it does not. */
export async function fetchDownloadsFolder(): Promise<string | null> {
	const data = await otter.query({ downloadsFolder: true });
	return data.downloadsFolder;
}

/** Opens the downloads folder in the file manager of the backend's machine. */
export async function openDownloadsFolder(): Promise<void> {
	await otter.mutation({ openDownloadsFolder: true });
}

/** Groups episodes under their series; movies stay alone. Keeps newest-first order. */
export function groupDownloads(items: DownloadItem[]): DownloadGroup[] {
	const groups: DownloadGroup[] = [];
	const byTitle = new Map<string, DownloadGroup>();
	for (const item of items) {
		const existing = byTitle.get(item.titleId);
		if (existing) {
			existing.items.push(item);
			continue;
		}
		const group = {
			titleId: item.titleId,
			titleName: item.titleName,
			imageUrl: item.imageUrl,
			posterUrl: item.posterUrl,
			items: [item]
		};
		byTitle.set(item.titleId, group);
		groups.push(group);
	}
	return groups;
}

export function isFinished(item: DownloadItem): boolean {
	return item.status === 'DONE' || item.status === 'FAILED';
}

/** "344.9 MB", "1.2 GB". */
export function formatSize(bytes: number): string {
	if (bytes <= 0) return '';
	const megabytes = bytes / (1024 * 1024);
	if (megabytes < 1024) return `${megabytes.toFixed(1)} MB`;
	return `${(megabytes / 1024).toFixed(1)} GB`;
}
