// Caption files of a movie or episode and the profile's caption preferences.
import { apiBase, otter } from './api';
import type { SubtitleSize } from './captions';
import { activeProfile } from './profile';
import type { Subtitle as GeneratedSubtitle } from '@neoworks-dev/otter-sdk/client';

export type Subtitle = Omit<GeneratedSubtitle, '__typename'>;

/** Caption files best first; empty when none are found or the lookup fails. */
export async function fetchSubtitles(mediaId: string, profileId: string | null): Promise<Subtitle[]> {
	const data = await otter.query({
		subtitles: {
			__args: { mediaId, profileId },
			id: true,
			language: true,
			label: true,
			release: true,
			hearingImpaired: true,
			source: true,
			vttUrl: true,
			srtUrl: true
		}
	});
	return data.subtitles;
}

/** Full URL of a caption file; the backend hands out paths. */
export function subtitleFileUrl(path: string): string {
	return `${apiBase()}${path}`;
}

export interface CaptionPreferenceChange {
	subtitlesEnabled?: boolean;
	// "" clears the choice.
	subtitleLanguage?: string;
	subtitleSize?: SubtitleSize;
	voiceBoost?: boolean;
}

/** Saves preference changes to the profile and to the signed-in profile of this app. */
export async function saveCaptionPreferences(profileId: string, change: CaptionPreferenceChange): Promise<void> {
	const data = await otter.mutation({
		updateProfile: {
			__args: { id: profileId, ...change },
			subtitlesEnabled: true,
			subtitleLanguage: true,
			subtitleSize: true,
			voiceBoost: true
		}
	});
	activeProfile.update(data.updateProfile);
}
