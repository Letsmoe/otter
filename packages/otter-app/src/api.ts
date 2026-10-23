import { createClient as createWsClient, type Client as WsClient } from 'graphql-ws/client';
// Import from the client subpath (not the barrel) so the SDK's Bun-specific
// plugin runtime never enters the browser build graph.
import { createOtterClient } from '@neoworks-dev/otter-sdk/client';

const DEFAULT_API_URL = 'http://localhost:8080';

/** Hosts that only mean "this machine", so their URLs are useless to other devices. */
const LOOPBACK_HOSTS = ['localhost', '127.0.0.1', '[::1]'];

let resolveApiBase: () => string = () => DEFAULT_API_URL;

/**
 * Tells the shared data layer where the backend lives. Each app calls this
 * once at startup (hooks); the resolver runs on every request, so it may
 * depend on runtime state such as a saved server URL.
 */
export function configureApi(resolver: () => string): void {
	resolveApiBase = resolver;
}

/** Base URL of the Go backend, without a trailing slash. */
export function apiBase(): string {
	return resolveApiBase().replace(/\/+$/, '');
}

function isBrowser(): boolean {
	return typeof window !== 'undefined';
}

// The endpoint is looked up per request so configureApi may run after import.
function fetchGraphql(_input: RequestInfo | URL, init?: RequestInit): Promise<Response> {
	return fetch(`${apiBase()}/graphql`, init);
}

/** Typed genql client for the Otter GraphQL API. */
export const otter = createOtterClient({ url: `${DEFAULT_API_URL}/graphql`, fetch: fetchGraphql as typeof fetch });

// GraphQL-over-WebSocket client for live subscriptions. Browser-only and lazily
// created so SSR never opens a socket. Uses the graphql-transport-ws subprotocol
// that gqlgen's websocket transport negotiates.
let wsClient: WsClient | null = null;

export function getWsClient(): WsClient | null {
	if (!isBrowser()) return null;
	if (wsClient) return wsClient;
	const wsUrl = `${apiBase()}/graphql`.replace(/^http/, 'ws');
	wsClient = createWsClient({ url: wsUrl, lazy: true, retryAttempts: Infinity });
	return wsClient;
}

/**
 * Points a backend-built URL at the address this client reaches the backend on.
 * The backend stamps blob and stream URLs with its PUBLIC_URL, which defaults to
 * localhost; from another device (the phone app) that host is unreachable.
 */
export function backendUrl(url: string): string {
	if (!url.startsWith('http://') && !url.startsWith('https://')) return url;
	const parsed = new URL(url);
	if (!LOOPBACK_HOSTS.includes(parsed.hostname)) return url;
	if (!parsed.pathname.startsWith('/api/')) return url;
	const base = apiBase();
	if (parsed.origin === new URL(base).origin) return url;
	return `${base}${parsed.pathname}${parsed.search}`;
}

/** Trims whitespace and trailing slashes and defaults the scheme to http. */
export function normalizeServerUrl(url: string): string {
	let normalized = url.trim().replace(/\/+$/, '');
	if (!/^https?:\/\//.test(normalized)) {
		normalized = `http://${normalized}`;
	}
	return normalized;
}

/** Whether the backend answers its health check at the given address. */
export async function checkServer(url: string): Promise<boolean> {
	try {
		const response = await fetch(`${normalizeServerUrl(url)}/health`, {
			signal: AbortSignal.timeout(5000)
		});
		return response.ok;
	} catch {
		return false;
	}
}
