import { loadTitleDetail } from '@neoworks-dev/otter-app/titleDetail';

export async function load({ params }) {
	const title = await loadTitleDetail(params.id);
	return { title, id: params.id };
}
