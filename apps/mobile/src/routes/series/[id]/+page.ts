import { loadTitleDetail } from '@neoworks-dev/otter-app/titleDetail';

function parseSeasonNumber(value: string | null): number | null {
	if (value === null) return null;
	const seasonNumber = Number(value);
	if (!Number.isInteger(seasonNumber)) return null;
	return seasonNumber;
}

export async function load({ params, url }) {
	const title = await loadTitleDetail(params.id);
	// Optional deep-link from the hero season selector or continue watching.
	const initialSeasonNumber = parseSeasonNumber(url.searchParams.get('season'));
	const initialEpisodeId = url.searchParams.get('episode');
	return { title, id: params.id, initialSeasonNumber, initialEpisodeId };
}
