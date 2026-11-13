import { otter } from '@letsmoe/otter-app/api';

const FALLBACK = {
	tmdbApiKey: '',
	tmdbLanguage: 'de-DE',
	requireVpn: false,
	errorReporting: true
};

export async function load() {
	try {
		const data = await otter.query({
			settings: { tmdbApiKey: true, tmdbLanguage: true, requireVpn: true, errorReporting: true }
		});
		return { settings: data.settings };
	} catch {
		return { settings: FALLBACK };
	}
}
