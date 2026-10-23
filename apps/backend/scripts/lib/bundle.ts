/**
 * Builds plugin packages for the backend's embedded (goja) runtime: one
 * self-contained CommonJS file plus a package.json whose "main" points at it,
 * the same layout the backend installs from npm (internal/pluginstore).
 */
import { existsSync } from 'node:fs';
import { mkdir, readdir, readFile, rename, rm, writeFile } from 'node:fs/promises';
import { basename, dirname, join, resolve } from 'node:path';

export const BACKEND_DIR = resolve(import.meta.dir, '../..');
export const PLUGINS_DIR = join(BACKEND_DIR, 'plugins');
export const SDK_DIR = resolve(BACKEND_DIR, '../../packages/otter-sdk/src');
const BUNDLE_FILE = 'plugin.js';
/** npm keyword that marks a package as an Otter plugin (pluginstore.Keyword). */
const PLUGIN_KEYWORD = 'otter-plugin';
/** Stripped from package names to name their folder (pluginstore.FolderName). */
const PACKAGE_PREFIX = 'otter-plugin-';

/** The package.json of a plugin's source. */
interface SourcePackage {
	name: string;
	version: string;
	description?: string;
	main: string;
}

/**
 * Local builds come from this checkout (dev); the backend never replaces them
 * with npm versions. Release builds are what gets published and shipped.
 */
export type BuildKind = 'local' | 'release';

/** Plugin source directories: every folder with a package.json that names an entry point. */
export async function pluginNames(): Promise<string[]> {
	const entries = await readdir(PLUGINS_DIR, { withFileTypes: true });
	const names: string[] = [];
	for (const entry of entries) {
		if (!entry.isDirectory()) continue;
		const packagePath = join(PLUGINS_DIR, entry.name, 'package.json');
		if (!existsSync(packagePath)) continue;
		const pkg = await Bun.file(packagePath).json();
		if (pkg.main) names.push(entry.name);
	}
	return names;
}

/** The source package.json of a plugin. */
export async function sourcePackage(name: string): Promise<SourcePackage> {
	return Bun.file(join(PLUGINS_DIR, name, 'package.json')).json();
}

/** Installed-plugin folder of a package: "@neoworks-dev/otter-plugin-eporner" → "eporner". */
export function folderName(packageName: string): string {
	const unscoped = packageName.slice(packageName.lastIndexOf('/') + 1);
	if (unscoped.startsWith(PACKAGE_PREFIX)) return unscoped.slice(PACKAGE_PREFIX.length);
	return unscoped;
}

/** The manifest written next to a bundle. */
function manifest(pkg: SourcePackage, kind: BuildKind): Record<string, unknown> {
	const content: Record<string, unknown> = {
		name: pkg.name,
		version: pkg.version,
		description: pkg.description,
		main: BUNDLE_FILE,
		keywords: [PLUGIN_KEYWORD]
	};
	if (kind === 'local') content.localBuild = true;
	return content;
}

/** Writes a file only when its content changed, via rename so readers never see half a file. */
async function writeAtomically(path: string, content: string): Promise<void> {
	if (existsSync(path) && (await readFile(path, 'utf8')) === content) return;
	const temporary = join(dirname(path), `.${basename(path)}.tmp`);
	await writeFile(temporary, content);
	await rename(temporary, path);
}

/** Bundles one plugin into outDir/<folder>/ and returns that folder. */
export async function bundlePlugin(name: string, outDir: string, kind: BuildKind): Promise<string> {
	const pkg = await sourcePackage(name);
	const result = await Bun.build({
		entrypoints: [join(PLUGINS_DIR, name, pkg.main)],
		target: 'node',
		format: 'cjs',
		minify: true,
		// The embedded runtime provides a native implementation.
		external: ['node-html-parser']
	});
	if (!result.success) {
		throw new Error(result.logs.map((log) => String(log)).join('\n'));
	}

	const target = join(outDir, folderName(pkg.name));
	await mkdir(target, { recursive: true });
	// Bundle first: the manifest is what makes the backend (re)load the plugin.
	await writeAtomically(join(target, BUNDLE_FILE), await result.outputs[0].text());
	await writeAtomically(join(target, 'package.json'), JSON.stringify(manifest(pkg, kind), null, 2));
	return target;
}

/**
 * Removes local builds whose plugin source is gone. Plugins installed from npm
 * share the folder and are left alone.
 */
export async function removeStaleLocalBuilds(folders: string[], outDir: string): Promise<void> {
	if (!existsSync(outDir)) return;
	for (const entry of await readdir(outDir, { withFileTypes: true })) {
		if (!entry.isDirectory() || folders.includes(entry.name)) continue;
		const manifestPath = join(outDir, entry.name, 'package.json');
		if (!existsSync(manifestPath)) continue;
		const installed = await Bun.file(manifestPath).json();
		if (installed.localBuild) {
			await rm(join(outDir, entry.name), { recursive: true, force: true });
		}
	}
}
