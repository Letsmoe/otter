import { callbackTarget, relayedCallbackUrl, renderReturnToAppPage } from './auth';
import { parseInvitePath, parseSharePath, tmdbLanguage } from './links';
import { renderInvitePage, renderNotFoundPage, renderSharePage } from './page';
import { fetchSharedTitle } from './tmdb';

/** Worker secrets, from apps/share/.env (`wrangler secret bulk .env`; CI writes it from GitHub secrets). */
export interface Environment {
	/** TMDB v3 API key. */
	TMDB_API_KEY: string;
	/**
	 * SHA-256 fingerprints of the key CI signs releases and nightlies with, comma-separated
	 * ("B2:B9:…:2E"). With them, Android opens otter.letsmoe.com links straight in Otter.
	 */
	ANDROID_CERT_FINGERPRINTS: string;
}

export function assetLinks(fingerprints: string): object[] | null {
	const certificateFingerprints = fingerprints
		.split(',')
		.map((fingerprint) => fingerprint.trim().toUpperCase())
		.filter((fingerprint) => fingerprint.length > 0);
	if (certificateFingerprints.length === 0) {
		return null;
	}
	return [
		{
			relation: ['delegate_permission/common.handle_all_urls'],
			target: { namespace: 'android_app', package_name: 'com.letsmoe.otter', sha256_cert_fingerprints: certificateFingerprints },
		},
	];
}

export default {
	async fetch(request: Request, environment: Environment): Promise<Response> {
		const url = new URL(request.url);
		if (url.pathname === '/.well-known/assetlinks.json') {
			const statements = assetLinks(environment.ANDROID_CERT_FINGERPRINTS ?? '');
			// Android remembers a failed check until the app updates; better none than an empty list.
			if (!statements) {
				return new Response('ANDROID_CERT_FINGERPRINTS is not set', { status: 503 });
			}
			return Response.json(statements, { headers: { 'Cache-Control': 'public, max-age=3600' } });
		}
		if (request.method !== 'GET' && request.method !== 'HEAD') {
			return new Response('Method not allowed', { status: 405 });
		}
		if (url.pathname === '/auth/callback') {
			return answerSignInCallback(url);
		}
		const roomCode = parseInvitePath(url.pathname);
		if (roomCode) {
			return html(renderInvitePage(roomCode, url), 200);
		}
		const target = parseSharePath(url.pathname);
		if (!target) {
			return html(renderNotFoundPage(), 404);
		}
		const language = tmdbLanguage(request.headers.get('Accept-Language'));
		const title = await fetchSharedTitle(target, environment.TMDB_API_KEY, language);
		if (!title) {
			return html(renderNotFoundPage(), 404);
		}
		return html(renderSharePage(title, url), 200);
	},
};

/** Neoworks sign-in lands here; the code goes on to the install that started it. */
function answerSignInCallback(url: URL): Response {
	const target = callbackTarget(url.searchParams.get('state'));
	if (!target) {
		return new Response('This sign-in did not start in Otter.', { status: 400 });
	}
	if (target.kind === 'web') {
		return new Response(null, {
			status: 302,
			headers: { Location: relayedCallbackUrl(target.origin, url), 'Cache-Control': 'no-store' },
		});
	}
	return new Response(renderReturnToAppPage(url), {
		headers: { 'Content-Type': 'text/html; charset=utf-8', 'Cache-Control': 'no-store' },
	});
}

function html(body: string, status: number): Response {
	return new Response(body, {
		status,
		headers: {
			'Content-Type': 'text/html; charset=utf-8',
			// Varies by Accept-Language, so only browsers cache it.
			'Cache-Control': 'private, max-age=3600',
		},
	});
}
