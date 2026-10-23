import adapter from '@sveltejs/adapter-static';

/** @type {import('@sveltejs/kit').Config} */
const config = {
	compilerOptions: {
		runes: ({ filename }) => (filename.split(/[/\\]/).includes('node_modules') ? undefined : true)
	},
	kit: {
		// A client-only SPA: Capacitor serves www/ from the APK.
		adapter: adapter({ pages: 'www', assets: 'www', fallback: 'index.html' })
	}
};

export default config;
