import tailwindcss from '@tailwindcss/vite';
import { sveltekit } from '@sveltejs/kit/vite';
import { defineConfig } from 'vite';

export default defineConfig({
	plugins: [tailwindcss(), sveltekit()],
	server: {
		port: 3001,
		fs: {
			// Allow reading the workspace root so the linked workspace packages resolve.
			allow: ['../..']
		}
	}
});
