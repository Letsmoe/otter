import { escapeHtml, openInAppUrl } from './page';

// Neoworks sign-in redirects every Otter install to the one registered URI,
// https://otter.letsmoe.com/auth/callback. `state` says where the code goes on:
//   app.<nonce>                 the Android app (App Links normally catch the redirect
//                               before it reaches this Worker)
//   web.<origin, base64url>.<nonce>   a web app at any address, e.g. a NAS
// Sign-in uses PKCE, so a code relayed elsewhere is useless without the verifier
// that only the starting device holds.

export type CallbackTarget = { kind: 'app' } | { kind: 'web'; origin: string };

/** Where the callback with this `state` belongs; null for a state Otter didn't make. */
export function callbackTarget(state: string | null): CallbackTarget | null {
	const parts = (state ?? '').split('.');
	if (parts[0] === 'app' && parts.length === 2 && parts[1].length > 0) {
		return { kind: 'app' };
	}
	if (parts[0] !== 'web' || parts.length !== 3 || parts[2].length === 0) {
		return null;
	}
	const origin = decodeOrigin(parts[1]);
	if (!origin) {
		return null;
	}
	return { kind: 'web', origin };
}

function decodeOrigin(encoded: string): string | null {
	let decoded: string;
	try {
		decoded = atob(encoded.replaceAll('-', '+').replaceAll('_', '/'));
	} catch {
		return null;
	}
	let url: URL;
	try {
		url = new URL(decoded);
	} catch {
		return null;
	}
	if (url.protocol !== 'https:' && url.protocol !== 'http:') {
		return null;
	}
	return url.origin;
}

/** The web app's own callback with the same query (code, state, error). */
export function relayedCallbackUrl(origin: string, callbackUrl: URL): string {
	return `${origin}/auth/callback${callbackUrl.search}`;
}

/** Shown when the app's callback opens in a browser instead of in Otter. */
export function renderReturnToAppPage(callbackUrl: URL): string {
	return `<!doctype html>
<html lang="en">
	<head>
		<meta charset="utf-8">
		<meta name="viewport" content="width=device-width, initial-scale=1">
		<meta name="robots" content="noindex">
		<title>Signed in · Otter</title>
		<style>
			body { margin: 0; min-height: 100vh; display: flex; align-items: center; justify-content: center;
				background: #0b0b0f; color: #fff; font-family: system-ui, -apple-system, "Segoe UI", sans-serif; text-align: center; }
			main { padding: 24px; display: flex; flex-direction: column; gap: 14px; align-items: center; }
			h1 { margin: 0; font-size: 26px; }
			p { margin: 0; color: rgba(255, 255, 255, 0.7); }
			a { padding: 12px 22px; border-radius: 999px; background: #fff; color: #000; font-weight: 600; text-decoration: none; }
		</style>
	</head>
	<body>
		<main>
			<h1>Signed in</h1>
			<p>Go back to Otter to finish.</p>
			<a href="${escapeHtml(openInAppUrl(callbackUrl))}">Return to Otter</a>
		</main>
	</body>
</html>`;
}
