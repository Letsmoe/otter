/**
 * Builds the backend binary for Android with the built-in TMDB key baked in
 * (settings.DefaultTMDBApiKey). The key comes from $TMDB_API_KEY, else from
 * TMDB_API_KEY in the repo root's .env; without one the build still works and
 * the app asks for a key on first start.
 *
 * Usage: bun scripts/build-server.ts --out <file>
 */
import { existsSync, readFileSync } from 'node:fs';
import { resolve } from 'node:path';

const BACKEND_DIR = resolve(import.meta.dir, '..');
const ENV_FILE = resolve(BACKEND_DIR, '../../.env');
const KEY_VARIABLE = 'github.com/neoworks/otter/internal/settings.DefaultTMDBApiKey';

function outputPath(): string {
	const index = process.argv.indexOf('--out');
	const value = process.argv[index + 1];
	if (index < 0 || !value) {
		console.error('usage: bun scripts/build-server.ts --out <file>');
		process.exit(1);
	}
	return resolve(process.cwd(), value);
}

function keyFromEnvFile(): string {
	if (!existsSync(ENV_FILE)) return '';
	for (const line of readFileSync(ENV_FILE, 'utf8').split('\n')) {
		const match = line.match(/^\s*TMDB_API_KEY\s*=\s*(.*)$/);
		if (match) return match[1].trim().replace(/^["']|["']$/g, '');
	}
	return '';
}

function tmdbKey(): string {
	const fromEnvironment = process.env.TMDB_API_KEY;
	if (fromEnvironment && fromEnvironment.trim()) return fromEnvironment.trim();
	return keyFromEnvFile();
}

const key = tmdbKey();
if (!key) console.warn('build-server: no TMDB_API_KEY found, building without a built-in key');

const flags = ['-s', '-w', '-checklinkname=0'];
if (key) flags.push(`-X ${KEY_VARIABLE}=${key}`);

const build = Bun.spawnSync(
	['go', 'build', '-trimpath', `-ldflags=${flags.join(' ')}`, '-o', outputPath(), './cmd/server'],
	{
		cwd: BACKEND_DIR,
		env: { ...process.env, CGO_ENABLED: '0', GOOS: 'android', GOARCH: 'arm64' },
		stdout: 'inherit',
		stderr: 'inherit'
	}
);
process.exit(build.exitCode ?? 1);
