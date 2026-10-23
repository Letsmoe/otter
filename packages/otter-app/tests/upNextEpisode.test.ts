import { describe, expect, test } from 'bun:test';
import { upNextEpisode, type SeasonEpisode } from '../src/seriesDetail';

function episode(seasonNumber: number, episodeNumber: number, progress?: number): SeasonEpisode {
	return {
		id: `episode:${seasonNumber}-${episodeNumber}`,
		seasonNumber,
		episodeNumber,
		name: null,
		overview: null,
		airDate: null,
		runtime: null,
		stillPath: null,
		progress,
		next: null
	};
}

function linked(episodes: SeasonEpisode[]): SeasonEpisode[] {
	return episodes.map((current, index) => {
		const following = episodes[index + 1];
		if (!following) return current;
		return {
			...current,
			next: {
				id: following.id,
				seasonNumber: following.seasonNumber,
				episodeNumber: following.episodeNumber,
				name: null
			}
		};
	});
}

describe('upNextEpisode', () => {
	test('starts with the first regular episode, skipping specials', () => {
		const upNext = upNextEpisode(linked([episode(0, 1), episode(1, 1), episode(1, 2)]));
		expect(upNext?.episode.id).toBe('episode:1-1');
		expect(upNext?.resume).toBe(false);
	});

	test('resumes the furthest started episode', () => {
		const upNext = upNextEpisode(linked([episode(1, 1, 1), episode(1, 2, 0.4), episode(1, 3)]));
		expect(upNext?.episode.id).toBe('episode:1-2');
		expect(upNext?.resume).toBe(true);
	});

	test('moves on to the next episode, across seasons, once finished', () => {
		const upNext = upNextEpisode(linked([episode(1, 1, 1), episode(1, 2, 0.95), episode(2, 1)]));
		expect(upNext?.episode.id).toBe('episode:2-1');
		expect(upNext?.resume).toBe(false);
	});

	test('returns null without episodes', () => {
		expect(upNextEpisode([])).toBeNull();
	});
});
