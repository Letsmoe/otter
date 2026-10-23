import type { CapacitorConfig } from '@capacitor/cli';

// The mobile client (this app's SvelteKit SPA) built into www/. It talks to the
// on-phone backend or a NAS, both over plain http, so the app is served from http://localhost (not https) and cleartext is on:
// otherwise every request to an http backend is blocked as mixed content.
const config: CapacitorConfig = {
	appId: 'dev.neoworks.otter.player',
	appName: 'Otter',
	webDir: 'www',
	plugins: {
		SplashScreen: {
			launchShowDuration: 2000,
			launchAutoHide: true,
			backgroundColor: '#0d0b1aff',
			androidSplashResourceName: 'splash',
			androidScaleType: 'CENTER_CROP',
			showSpinner: false
		}
	},
	server: {
		androidScheme: 'http',
		cleartext: true
	}
};

// Live reload: point the app at a running web dev server instead of www/.
if (process.env.CAP_SERVER_URL) {
	config.server!.url = process.env.CAP_SERVER_URL;
}

export default config;
