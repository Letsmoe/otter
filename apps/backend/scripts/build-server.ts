/**
 * Builds the backend binary for Android with the built-in TMDB key baked in
 * (settings.DefaultTMDBApiKey). The key comes from $TMDB_API_KEY, else from
 * TMDB_API_KEY in the repo root's .env; without one the build still works and
 * the app asks for a key on first start.
 *
 * libneoworks (Neoworks sync) compiles C and C++ through cgo, so the build uses
 * the Android NDK's clang: $ANDROID_NDK_HOME, $ANDROID_NDK_LATEST_HOME (GitHub
 * runners), else the newest NDK under $ANDROID_HOME, $ANDROID_SDK_ROOT or ~/Android/Sdk.
 *
 * Usage: bun scripts/build-server.ts --out <file>
 */
import { existsSync, mkdirSync, readdirSync, readFileSync, writeFileSync } from 'node:fs';
import { homedir, tmpdir } from 'node:os';
import { join, resolve } from 'node:path';

const BACKEND_DIR = resolve(import.meta.dir, '..');
const ENV_FILE = resolve(BACKEND_DIR, '../../.env');
const KEY_VARIABLE = 'github.com/Letsmoe/otter/internal/settings.DefaultTMDBApiKey';
// The app's minSdk; the NDK names its compilers after it.
const ANDROID_API_LEVEL = 26;

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

function newestNdkIn(sdkDirectory: string | undefined): string | null {
	if (!sdkDirectory) return null;
	const ndkDirectory = join(sdkDirectory, 'ndk');
	if (!existsSync(ndkDirectory)) return null;
	const versions = readdirSync(ndkDirectory).sort((left, right) =>
		left.localeCompare(right, undefined, { numeric: true })
	);
	const newest = versions.at(-1);
	if (!newest) return null;
	return join(ndkDirectory, newest);
}

function ndkDirectory(): string {
	const candidates = [
		process.env.ANDROID_NDK_HOME,
		process.env.ANDROID_NDK_LATEST_HOME,
		newestNdkIn(process.env.ANDROID_HOME),
		newestNdkIn(process.env.ANDROID_SDK_ROOT),
		newestNdkIn(join(homedir(), 'Android/Sdk'))
	];
	for (const candidate of candidates) {
		if (candidate && existsSync(candidate)) return candidate;
	}
	console.error('build-server: no Android NDK found; install one with the SDK manager or set ANDROID_NDK_HOME');
	process.exit(1);
}

/** The NDK's clang for arm64 Android at the app's API level. */
function ndkCompilers(): { cc: string; cxx: string } {
	const hostTag = `${process.platform}-x86_64`;
	const binDirectory = join(ndkDirectory(), 'toolchains/llvm/prebuilt', hostTag, 'bin');
	const prefix = join(binDirectory, `aarch64-linux-android${ANDROID_API_LEVEL}-clang`);
	return { cc: prefix, cxx: `${prefix}++` };
}

/**
 * libneoworks asks for -lpthread on every Linux-like target, but Android's libc
 * has pthread built in and the NDK ships no libpthread. An empty archive (just
 * the ar header) satisfies the linker without adding anything.
 */
function emptyPthreadDirectory(): string {
	const directory = join(tmpdir(), 'otter-android-link');
	mkdirSync(directory, { recursive: true });
	writeFileSync(join(directory, 'libpthread.a'), '!<arch>\n');
	return directory;
}

const key = tmdbKey();
if (!key) console.warn('build-server: no TMDB_API_KEY found, building without a built-in key');

const flags = ['-s', '-w', '-checklinkname=0'];
if (key) flags.push(`-X ${KEY_VARIABLE}=${key}`);

const compilers = ndkCompilers();
const build = Bun.spawnSync(
	['go', 'build', '-trimpath', `-ldflags=${flags.join(' ')}`, '-o', outputPath(), './cmd/server'],
	{
		cwd: BACKEND_DIR,
		env: {
			...process.env,
			CGO_ENABLED: '1',
			CC: compilers.cc,
			CXX: compilers.cxx,
			CGO_LDFLAGS: `-L${emptyPthreadDirectory()}`,
			GOOS: 'android',
			GOARCH: 'arm64'
		},
		stdout: 'inherit',
		stderr: 'inherit'
	}
);
process.exit(build.exitCode ?? 1);
