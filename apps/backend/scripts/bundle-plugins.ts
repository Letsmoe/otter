/**
 * Bundles every plugin under apps/backend/plugins into a plugin folder,
 * <out>/<folder>/plugin.js + package.json each (see lib/bundle.ts). The
 * backend hot-reloads a plugin when its bundle changes.
 *
 * Usage: bun scripts/bundle-plugins.ts [--watch] [--release] [--out <dir>]
 *   --out      default: $DATA_DIR/plugins, DATA_DIR defaulting to ./data
 *   --watch    rebuild a plugin whenever its source (or the SDK) changes
 *   --release  build what npm and the phone app ship (the backend updates
 *              these from npm) into an emptied --out; default is local builds
 */
import { watch } from 'node:fs';
import { rm } from 'node:fs/promises';
import { resolve, sep } from 'node:path';
import {
	BACKEND_DIR,
	type BuildKind,
	bundlePlugin,
	folderName,
	PLUGINS_DIR,
	pluginNames,
	removeStaleLocalBuilds,
	SDK_DIR,
	sourcePackage
} from './lib/bundle';

// Several file events arrive per save; rebuild once they settle.
const WATCH_DEBOUNCE_MS = 150;

/** Output folder from --out, else $DATA_DIR/plugins. */
function outputDir(): string {
	const flagIndex = process.argv.indexOf('--out');
	if (flagIndex !== -1) return resolve(process.argv[flagIndex + 1]);
	return resolve(BACKEND_DIR, process.env.DATA_DIR ?? 'data', 'plugins');
}

/** Bundles one plugin, reporting instead of throwing so watch mode keeps going. */
async function bundleAndReport(name: string, outDir: string, kind: BuildKind): Promise<void> {
	const started = performance.now();
	try {
		await bundlePlugin(name, outDir, kind);
		console.log(`bundled ${name} in ${Math.round(performance.now() - started)}ms`);
	} catch (error) {
		console.error(`bundle ${name} failed:\n${error}`);
		process.exitCode = 1;
	}
}

/** The plugin a changed source path belongs to, or undefined for shared code (the SDK). */
function pluginOfPath(path: string): string | undefined {
	const [name, ...rest] = path.split(sep);
	if (rest.includes('node_modules') || rest.includes('tests') || rest.includes('test')) return undefined;
	return name;
}

/** Rebuilds plugins as their sources change; an SDK change rebuilds them all. */
function watchSources(names: string[], outDir: string): void {
	const pending = new Set<string>();
	let timer: ReturnType<typeof setTimeout> | undefined;
	const schedule = (plugins: string[]) => {
		for (const plugin of plugins) pending.add(plugin);
		clearTimeout(timer);
		timer = setTimeout(async () => {
			const batch = [...pending];
			pending.clear();
			for (const plugin of batch) await bundleAndReport(plugin, outDir, 'local');
		}, WATCH_DEBOUNCE_MS);
	};

	watch(PLUGINS_DIR, { recursive: true }, (_event, file) => {
		if (!file || !file.endsWith('.ts')) return;
		const plugin = pluginOfPath(file);
		if (plugin && names.includes(plugin)) schedule([plugin]);
	});
	watch(SDK_DIR, { recursive: true }, () => schedule(names));
	console.log(`watching ${names.length} plugins`);
}

const outDir = outputDir();
const names = await pluginNames();
let kind: BuildKind = 'local';
if (process.argv.includes('--release')) {
	kind = 'release';
	await rm(outDir, { recursive: true, force: true });
} else {
	const folders = await Promise.all(names.map(async (name) => folderName((await sourcePackage(name)).name)));
	await removeStaleLocalBuilds(folders, outDir);
}
await Promise.all(names.map((name) => bundleAndReport(name, outDir, kind)));
if (process.argv.includes('--watch')) watchSources(names, outDir);
