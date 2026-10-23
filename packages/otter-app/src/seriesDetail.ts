import { otter } from './api';
import type { EpisodeRef } from './discovery';

export interface SeasonEpisode {
	id: string;
	seasonNumber: number;
	episodeNumber: number;
	name: string | null;
	overview: string | null;
	airDate: string | null;
	runtime: number | null;
	stillPath: string | null;
	// Watch fraction (0..1) for the active profile; undefined when unwatched.
	progress?: number;
	// The episode after this one, crossing seasons.
	next: EpisodeRef | null;
}

const EPISODE_SELECT = {
	id: true,
	seasonNumber: true,
	episodeNumber: true,
	name: true,
	overview: true,
	airDate: true,
	runtime: true,
	stillPath: true,
	next: { id: true, seasonNumber: true, episodeNumber: true, name: true }
} as const;

interface ProgressRef {
	progressSeconds: number;
	durationSeconds: number;
}

function progressFraction(progress: ProgressRef | null): number | undefined {
	if (!progress || progress.durationSeconds <= 0) return undefined;
	return Math.min(1, progress.progressSeconds / progress.durationSeconds);
}

/**
 * Every episode of a series, all seasons in one call. The backend fetches each
 * season's episodes from TMDB on first read.
 */
export async function loadEpisodes(seriesId: string, profileId: string): Promise<SeasonEpisode[]> {
	try {
		const data = await otter.query({
			title: {
				__args: { id: seriesId },
				seasons: {
					episodes: {
						...EPISODE_SELECT,
						progress: { __args: { profileId }, progressSeconds: true, durationSeconds: true }
					}
				}
			}
		});
		if (!data.title) return [];
		const episodes = data.title.seasons.flatMap((season) => season.episodes);
		return episodes.map((episode) => ({ ...episode, progress: progressFraction(episode.progress) }));
	} catch {
		return [];
	}
}

// Watched this far, an episode counts as finished and Play moves on.
const FINISHED_FRACTION = 0.9;

export interface UpNext {
	episode: SeasonEpisode;
	/** True when the episode was started and Play continues it. */
	resume: boolean;
}

function firstRegularEpisode(episodes: SeasonEpisode[]): SeasonEpisode | undefined {
	const regular = episodes.find((episode) => episode.seasonNumber > 0);
	if (regular) return regular;
	return episodes[0];
}

/**
 * The episode Play starts: the furthest started episode (in series order), or
 * the one after it when finished, else the first regular episode.
 */
export function upNextEpisode(episodes: SeasonEpisode[]): UpNext | null {
	const started = episodes.filter((episode) => episode.progress !== undefined && episode.seasonNumber > 0);
	const furthest = started.at(-1);
	if (!furthest) {
		const first = firstRegularEpisode(episodes);
		if (!first) return null;
		return { episode: first, resume: false };
	}
	if ((furthest.progress || 0) < FINISHED_FRACTION) return { episode: furthest, resume: true };
	const following = episodes.find((episode) => episode.id === furthest.next?.id);
	if (following) return { episode: following, resume: false };
	return { episode: furthest, resume: false };
}

/** "S1:E3", Netflix's short episode code. */
export function episodeCode(episode: { seasonNumber: number; episodeNumber: number }): string {
	return `S${episode.seasonNumber}:E${episode.episodeNumber}`;
}
