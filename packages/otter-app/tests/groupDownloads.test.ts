import { describe, expect, test } from 'bun:test';
import { formatSize, groupDownloads, type DownloadItem } from '../src/downloads';

function item(id: string, titleId: string): DownloadItem {
	return {
		id,
		status: 'DONE',
		progress: 1,
		sizeBytes: 0,
		error: null,
		mediaId: id,
		titleId,
		titleName: titleId,
		imageUrl: null,
		posterUrl: null,
		episode: null,
		url: null,
		mimeType: null
	};
}

describe('groupDownloads', () => {
	test('puts episodes of one series together, in first-seen order', () => {
		const groups = groupDownloads([
			item('a', 'series:1'),
			item('b', 'movie:2'),
			item('c', 'series:1')
		]);
		expect(groups.map((group) => group.titleId)).toEqual(['series:1', 'movie:2']);
		expect(groups[0].items.map((entry) => entry.id)).toEqual(['a', 'c']);
	});
});

describe('formatSize', () => {
	test('uses MB below a gigabyte and GB above', () => {
		expect(formatSize(344.9 * 1024 * 1024)).toBe('344.9 MB');
		expect(formatSize(1.5 * 1024 * 1024 * 1024)).toBe('1.5 GB');
		expect(formatSize(0)).toBe('');
	});
});
