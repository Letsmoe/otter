import { loadPersonDetail } from '@letsmoe/otter-app/personDetail';

export async function load({ params }) {
	const person = await loadPersonDetail(params.id);
	return { person, id: params.id };
}
