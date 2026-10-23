import { otter } from '@neoworks-dev/otter-app/api';

const FALLBACK = {
	tmdbApiKey: '',
	tmdbLanguage: 'de-DE',
	requireVpn: false
};

export async function load() {
	try {
		const data = await otter.query({
			settings: { tmdbApiKey: true, tmdbLanguage: true, requireVpn: true }
		});
		return { settings: data.settings };
	} catch {
		return { settings: FALLBACK };
	}
}
