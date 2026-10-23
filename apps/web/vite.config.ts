import tailwindcss from '@tailwindcss/vite';
import { sveltekit } from '@sveltejs/kit/vite';
import { defineConfig } from 'vite';

export default defineConfig({
	plugins: [tailwindcss(), sveltekit()],
	// otter-sdk is a bun-linked package shipping raw .ts; Vite must transpile it
	// (don't externalize for SSR) and be allowed to read outside the project root.
	ssr: {
		noExternal: ['@neoworks-dev/otter-sdk', '@neoworks-dev/otter-app']
	},
	server: {
		fs: {
			// Allow reading the workspace root so the linked otter-sdk source resolves.
			allow: ['../..']
		}
	}
});
