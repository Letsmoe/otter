// The active profile's downloads, shared by the Downloads tab, the profile
// card and the download buttons. Polls while a download is still running.
import {
	deleteDownload,
	fetchDownloads,
	isFinished,
	startDownload,
	type DownloadItem
} from '@neoworks-dev/otter-app/downloads';

const POLL_INTERVAL_MS = 2000;

class DownloadsState {
	items = $state<DownloadItem[]>([]);
	loaded = $state(false);

	#profileId: string | null = null;
	#pollTimer: ReturnType<typeof setTimeout> | undefined;

	/** Switches to a profile's downloads (null when nobody is signed in). */
	follow(profileId: string | null) {
		if (profileId === this.#profileId) return;
		this.#profileId = profileId;
		this.items = [];
		this.loaded = false;
		this.refresh();
	}

	async refresh() {
		clearTimeout(this.#pollTimer);
		const profileId = this.#profileId;
		if (!profileId) return;
		const loaded = await fetchDownloads(profileId);
		if (profileId !== this.#profileId) return;
		this.items = loaded;
		this.loaded = true;
		if (loaded.some((item) => !isFinished(item))) {
			this.#pollTimer = setTimeout(() => this.refresh(), POLL_INTERVAL_MS);
		}
	}

	forMedia(mediaId: string): DownloadItem | undefined {
		return this.items.find((item) => item.mediaId === mediaId);
	}

	async start(mediaId: string, streamId?: string) {
		if (!this.#profileId) return;
		await startDownload(this.#profileId, mediaId, streamId);
		await this.refresh();
	}

	async remove(id: string) {
		this.items = this.items.filter((item) => item.id !== id);
		await deleteDownload(id);
		await this.refresh();
	}
}

export const downloads = new DownloadsState();
