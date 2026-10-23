import { fetchPlugins } from '@neoworks-dev/otter-app/plugins';

export async function load() {
	return { plugins: await fetchPlugins() };
}
