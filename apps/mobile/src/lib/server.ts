import { env } from '$env/dynamic/public';
import { Capacitor } from '@capacitor/core';
import { checkServer, normalizeServerUrl } from '@neoworks-dev/otter-app/api';

/** Backend address in a desktop browser during development. */
const DEFAULT_API_URL = 'http://localhost:8080';

/**
 * The backend the phone app runs itself (EmbeddedBackend.java, loopback only).
 * The native app uses it unless another server is saved in Settings.
 */
export const EMBEDDED_API_URL = 'http://127.0.0.1:47832';

/** How long the app waits for its on-device backend to come up at launch. */
const EMBEDDED_STARTUP_TIMEOUT_MS = 30_000;

/** localStorage key holding the backend address picked in Settings. */
const SERVER_URL_STORAGE_KEY = 'otter:server-url';

/** Whether the client runs inside the native (Capacitor) app. */
export function isNativeApp(): boolean {
	return Capacitor.isNativePlatform();
}

/** The backend address saved on this device, if any. */
export function storedServerUrl(): string | null {
	return localStorage.getItem(SERVER_URL_STORAGE_KEY);
}

/** Saves the backend address for this device; takes effect on the next page load. */
export function saveServerUrl(url: string): void {
	localStorage.setItem(SERVER_URL_STORAGE_KEY, normalizeServerUrl(url));
}

/** Forgets the saved address, so the app goes back to its default backend. */
export function clearServerUrl(): void {
	localStorage.removeItem(SERVER_URL_STORAGE_KEY);
}

/** Whether the client talks to the backend running on this phone. */
export function usesEmbeddedBackend(): boolean {
	return isNativeApp() && storedServerUrl() === null;
}

/**
 * Base URL of the Go backend: the device's saved address, else the on-device
 * backend in the native app, else PUBLIC_API_URL (desktop testing).
 */
export function resolveApiBase(): string {
	const stored = storedServerUrl();
	if (stored) return stored;
	if (isNativeApp()) return EMBEDDED_API_URL;
	if (env.PUBLIC_API_URL) return env.PUBLIC_API_URL;
	return DEFAULT_API_URL;
}

/**
 * Waits until the on-device backend answers, which takes a moment after the
 * app starts. Returns false if it doesn't come up in time.
 */
export async function waitForEmbeddedBackend(): Promise<boolean> {
	const deadline = Date.now() + EMBEDDED_STARTUP_TIMEOUT_MS;
	while (Date.now() < deadline) {
		if (await checkServer(EMBEDDED_API_URL)) return true;
		await new Promise((resolve) => setTimeout(resolve, 250));
	}
	return false;
}
