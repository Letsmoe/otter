import { afterEach, describe, expect, mock, test } from 'bun:test';
import { callbackTarget } from './auth';
import worker, { assetLinks, type Environment } from './index';
import { parseInvitePath, parseSharePath, tmdbLanguage } from './links';
import { escapeHtml, openInAppUrl } from './page';

const fingerprint = 'B2:B9:10:ED:ED:C1:EC:13:B5:F7:E8:70:F2:FF:C3:82:78:E3:8A:6B:D4:13:51:CE:7C:67:64:33:90:3E:12:2E';
const environment: Environment = { TMDB_API_KEY: 'test-key', ANDROID_CERT_FINGERPRINTS: fingerprint };
const originalFetch = globalThis.fetch;

/** Answers TMDB requests from [answers] by path; anything else is a 404. */
function fakeTmdb(answers: Record<string, object>): string[] {
	const requestedUrls: string[] = [];
	globalThis.fetch = mock(async (input: string | URL | Request) => {
		const url = new URL(String(input));
		requestedUrls.push(url.toString());
		const answer = answers[url.pathname.replace('/3', '')];
		if (!answer) {
			return new Response('{}', { status: 404 });
		}
		return Response.json(answer);
	}) as unknown as typeof fetch;
	return requestedUrls;
}

function visit(path: string, headers: Record<string, string> = {}): Promise<Response> {
	return worker.fetch(new Request(`https://otter.letsmoe.com${path}`, { headers }), environment);
}

afterEach(() => {
	globalThis.fetch = originalFetch;
});

describe('share links', () => {
	test('read the same paths the app writes', () => {
		expect(parseSharePath('/movie/603-the-matrix')).toEqual({ kind: 'movie', tmdbId: 603 });
		expect(parseSharePath('/tv/1396-breaking-bad')).toEqual({ kind: 'series', tmdbId: 1396 });
		expect(parseSharePath('/tv/1396-breaking-bad/s2e13')).toEqual({ kind: 'episode', tmdbId: 1396, seasonNumber: 2, episodeNumber: 13 });
		expect(parseSharePath('/movie/603')).toEqual({ kind: 'movie', tmdbId: 603 });
	});

	test('ignore everything else', () => {
		expect(parseSharePath('/')).toBeNull();
		expect(parseSharePath('/person/500')).toBeNull();
		expect(parseSharePath('/movie/the-matrix')).toBeNull();
	});

	test('ask TMDB in the visitor language', () => {
		expect(tmdbLanguage('de-DE,de;q=0.9,en;q=0.8')).toBe('de-DE');
		expect(tmdbLanguage('fr')).toBe('fr');
		expect(tmdbLanguage(null)).toBe('en-US');
		expect(tmdbLanguage('*')).toBe('en-US');
	});
});

describe('the share page', () => {
	test('carries the preview chat apps show', async () => {
		fakeTmdb({
			'/movie/603': { title: 'The Matrix', release_date: '1999-03-30', overview: 'A hacker learns the truth.', poster_path: '/poster.jpg', backdrop_path: '/backdrop.jpg' },
		});
		const response = await visit('/movie/603-the-matrix');
		const page = await response.text();
		expect(response.status).toBe(200);
		expect(page).toContain('<meta property="og:title" content="The Matrix (1999)">');
		expect(page).toContain('<meta property="og:image" content="https://image.tmdb.org/t/p/w1280/backdrop.jpg">');
		expect(page).toContain('<meta property="og:type" content="video.movie">');
		expect(page).toContain('A hacker learns the truth.');
	});

	test('names the episode with its series', async () => {
		fakeTmdb({
			'/tv/1396': { name: 'Breaking Bad', first_air_date: '2008-01-20', poster_path: '/series.jpg' },
			'/tv/1396/season/1/episode/1': { name: 'Pilot', overview: 'A teacher turns cook.', still_path: '/still.jpg' },
		});
		const page = await (await visit('/tv/1396-breaking-bad/s1e1')).text();
		expect(page).toContain('<meta property="og:title" content="Breaking Bad S1:E1 · Pilot">');
		expect(page).toContain('https://image.tmdb.org/t/p/w1280/still.jpg');
	});

	test('passes the browser language to TMDB', async () => {
		const requestedUrls = fakeTmdb({ '/movie/603': { title: 'Matrix' } });
		await visit('/movie/603', { 'Accept-Language': 'de-DE,de;q=0.9' });
		expect(requestedUrls[0]).toContain('language=de-DE');
	});

	test('escapes what TMDB sends', async () => {
		fakeTmdb({ '/movie/1': { title: '<script>alert(1)</script>', overview: '"quoted" & more' } });
		const page = await (await visit('/movie/1')).text();
		expect(page).not.toContain('<script>alert(1)</script>');
		expect(page).toContain('&lt;script&gt;alert(1)&lt;/script&gt;');
		expect(escapeHtml(`"'<>&`)).toBe('&quot;&#39;&lt;&gt;&amp;');
	});

	test('is a 404 for titles TMDB does not know', async () => {
		fakeTmdb({});
		expect((await visit('/movie/999999999')).status).toBe(404);
		expect((await visit('/something-else')).status).toBe(404);
	});

	test('opens the same address in Otter', () => {
		expect(openInAppUrl(new URL('https://otter.letsmoe.com/movie/603-the-matrix'))).toStartWith(
			'intent://otter.letsmoe.com/movie/603-the-matrix#Intent;scheme=https;package=com.letsmoe.otter;',
		);
	});
});

describe('Watch Together invites', () => {
	test('read the room code the apps put in the link', () => {
		expect(parseInvitePath('/together/K7QD9MZA')).toBe('K7QD9MZA');
		expect(parseInvitePath('/together/k7qd-9mza')).toBe('K7QD9MZA');
		expect(parseInvitePath('/together/O0I1O0I1')).toBeNull();
		expect(parseInvitePath('/together/K7QD9MZA/extra')).toBeNull();
	});

	test('show the code without asking TMDB', async () => {
		const requestedUrls = fakeTmdb({});
		const response = await visit('/together/K7QD9MZA');
		const page = await response.text();
		expect(response.status).toBe(200);
		expect(page).toContain('K7QD-9MZA');
		expect(page).toContain('<meta property="og:title" content="Watch together on Otter">');
		expect(page).toContain('intent://otter.letsmoe.com/together/K7QD9MZA#Intent;');
		expect(requestedUrls).toEqual([]);
	});
});

describe('the sign-in callback', () => {
	const nasOrigin = btoa('http://nas.local:3000').replaceAll('+', '-').replaceAll('/', '_').replaceAll('=', '');

	test('relays a web sign-in to the address that started it', async () => {
		const response = await visit(`/auth/callback?code=abc&state=web.${nasOrigin}.n0nce`);
		expect(response.status).toBe(302);
		expect(response.headers.get('Location')).toBe(`http://nas.local:3000/auth/callback?code=abc&state=web.${nasOrigin}.n0nce`);
	});

	test('sends only to the origin, never to a path the state smuggles in', () => {
		const withPath = btoa('https://evil.example/steal?x=').replaceAll('=', '');
		expect(callbackTarget(`web.${withPath}.n0nce`)).toEqual({ kind: 'web', origin: 'https://evil.example' });
		expect(callbackTarget(`web.${btoa('javascript:alert(1)')}.n0nce`)).toBeNull();
	});

	test('offers the way back into the app with the code', async () => {
		const response = await visit('/auth/callback?code=abc&state=app.n0nce');
		const page = await response.text();
		expect(response.status).toBe(200);
		expect(page).toContain('intent://otter.letsmoe.com/auth/callback?code=abc&amp;state=app.n0nce#Intent;');
	});

	test('refuses a state Otter did not make', async () => {
		expect((await visit('/auth/callback?code=abc&state=other')).status).toBe(400);
		expect((await visit('/auth/callback?code=abc')).status).toBe(400);
		expect(callbackTarget('web.%%%.n0nce')).toBeNull();
	});
});

describe('assetlinks.json', () => {
	test('names the app and its signing key', async () => {
		const response = await visit('/.well-known/assetlinks.json');
		const statements = (await response.json()) as Array<{ target: { package_name: string; sha256_cert_fingerprints: string[] } }>;
		expect(response.headers.get('Content-Type')).toContain('application/json');
		expect(statements[0].target.package_name).toBe('com.letsmoe.otter');
		expect(statements[0].target.sha256_cert_fingerprints).toEqual([fingerprint]);
	});

	test('takes several fingerprints and is missing without any', () => {
		expect(assetLinks(' aa:bb , cc:dd ')).toEqual([
			expect.objectContaining({ target: expect.objectContaining({ sha256_cert_fingerprints: ['AA:BB', 'CC:DD'] }) }),
		]);
		expect(assetLinks('')).toBeNull();
	});
});
