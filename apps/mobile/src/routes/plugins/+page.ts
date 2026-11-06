import { fetchPlugins } from '@letsmoe/otter-app/plugins';

export async function load() {
	return { plugins: await fetchPlugins() };
}
