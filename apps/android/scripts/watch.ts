/**
 * Poor man's hot reload: rebuilds and reinstalls the debug app whenever a file
 * under app/src changes, then restarts it on the connected phone. Kotlin-only:
 * the Go backend (libotter.so) and plugin bundles are reused as they are, which
 * keeps a round trip around 10 s. Run `bun run build` first after Go changes.
 *
 * Usage: bun scripts/watch.ts
 */
import { watch } from 'node:fs';
import { resolve } from 'node:path';

const ANDROID_DIR = resolve(import.meta.dir, '..');
const SOURCE_DIR = resolve(ANDROID_DIR, 'app/src');
const ACTIVITY = 'com.letsmoe.otter/com.letsmoe.otter.MainActivity';
// Agents write several files in a row; wait for them to finish before building.
const SETTLE_MILLISECONDS = 1000;

let settleTimer: Timer | undefined;
let building = false;
let changedWhileBuilding = false;

async function run(command: string[]): Promise<boolean> {
	const child = Bun.spawn(command, { cwd: ANDROID_DIR, stdout: 'inherit', stderr: 'inherit' });
	return (await child.exited) === 0;
}

async function installAndRestart(): Promise<void> {
	building = true;
	const startedAt = performance.now();
	console.log('\n[watch] building…');
	const installed = await run(['./gradlew', 'installDebug', '-q']);
	if (installed) {
		// -S stops the running app first, so the new code is what starts.
		await run(['adb', 'shell', 'am', 'start', '-S', '-n', ACTIVITY]);
		const seconds = ((performance.now() - startedAt) / 1000).toFixed(1);
		console.log(`[watch] reloaded in ${seconds} s`);
	} else {
		console.log('[watch] build failed, waiting for the next change');
	}
	building = false;
	if (changedWhileBuilding) {
		changedWhileBuilding = false;
		await installAndRestart();
	}
}

function onChange(): void {
	if (building) {
		changedWhileBuilding = true;
		return;
	}
	clearTimeout(settleTimer);
	settleTimer = setTimeout(installAndRestart, SETTLE_MILLISECONDS);
}

watch(SOURCE_DIR, { recursive: true }, onChange);
console.log(`[watch] watching ${SOURCE_DIR}`);
