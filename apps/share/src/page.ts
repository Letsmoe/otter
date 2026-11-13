import type { SharedTitle } from './tmdb';

const appPackage = 'com.letsmoe.otter';
export const downloadUrl = 'https://github.com/Letsmoe/otter/releases/latest';
const projectUrl = 'https://letsmoe.com/projects/otter';

export function escapeHtml(text: string): string {
	return text
		.replaceAll('&', '&amp;')
		.replaceAll('<', '&lt;')
		.replaceAll('>', '&gt;')
		.replaceAll('"', '&quot;')
		.replaceAll("'", '&#39;');
}

/**
 * Opens the link in Otter from a browser (in-app browsers, or before Android verified the
 * site); Chrome goes to the download page when Otter isn't installed.
 */
export function openInAppUrl(pageUrl: URL): string {
	const fallback = encodeURIComponent(downloadUrl);
	return `intent://${pageUrl.host}${pageUrl.pathname}${pageUrl.search}#Intent;scheme=https;package=${appPackage};S.browser_fallback_url=${fallback};end`;
}

/** The page a shared link shows without Otter, with the tags chat apps read for their preview. */
export function renderSharePage(title: SharedTitle, pageUrl: URL): string {
	const description = shorten(title.overview, 200);
	const previewTitle = [title.heading, title.subheading].filter(Boolean).join(' · ');
	const previewImage = title.previewImageUrl ?? title.posterUrl;
	const metaTags = [
		meta('property', 'og:type', title.previewType),
		meta('property', 'og:site_name', 'Otter'),
		meta('property', 'og:title', previewTitle),
		meta('property', 'og:description', description),
		meta('property', 'og:url', pageUrl.toString()),
		previewImage ? meta('property', 'og:image', previewImage) : '',
		meta('name', 'twitter:card', previewImage ? 'summary_large_image' : 'summary'),
		meta('name', 'description', description),
	].join('\n\t\t');
	const poster = title.posterUrl
		? `<img class="poster" src="${escapeHtml(title.posterUrl)}" alt="" width="200" height="300">`
		: '';
	const subheading = title.subheading ? `<p class="subheading">${escapeHtml(title.subheading)}</p>` : '';
	const backdrop = previewImage ? `style="--backdrop: url('${escapeHtml(previewImage)}')"` : '';
	return layout(
		previewTitle,
		metaTags,
		`<main ${backdrop}>
			<article>
				${poster}
				<div class="details">
					<p class="eyebrow">Shared with you on Otter</p>
					<h1>${escapeHtml(title.heading)}</h1>
					${subheading}
					<p class="overview">${escapeHtml(title.overview)}</p>
					<div class="actions">
						<a class="button primary" href="${escapeHtml(openInAppUrl(pageUrl))}">Open in Otter</a>
						<a class="button" href="${downloadUrl}">Get Otter for Android</a>
					</div>
				</div>
			</article>
		</main>`,
	);
}

/** A Watch Together invite for someone without Otter, or whose browser opened the link. */
export function renderInvitePage(roomCode: string, pageUrl: URL): string {
	const formattedCode = `${roomCode.slice(0, 4)}-${roomCode.slice(4)}`;
	const description = `Join room ${formattedCode} and watch in sync, with chat and reactions.`;
	const metaTags = [
		meta('property', 'og:type', 'website'),
		meta('property', 'og:site_name', 'Otter'),
		meta('property', 'og:title', 'Watch together on Otter'),
		meta('property', 'og:description', description),
		meta('property', 'og:url', pageUrl.toString()),
		meta('name', 'twitter:card', 'summary'),
		meta('name', 'robots', 'noindex'),
	].join('\n\t\t');
	return layout(
		'Watch together',
		metaTags,
		`<main>
			<article>
				<div class="details">
					<p class="eyebrow">You're invited</p>
					<h1>Watch together on Otter</h1>
					<p class="overview">${escapeHtml(description)} Without the link, enter the code under Watch Together in Otter.</p>
					<p class="room-code">${escapeHtml(formattedCode)}</p>
					<div class="actions">
						<a class="button primary" href="${escapeHtml(openInAppUrl(pageUrl))}">Join in Otter</a>
						<a class="button" href="${downloadUrl}">Get Otter for Android</a>
					</div>
				</div>
			</article>
		</main>`,
	);
}

export function renderNotFoundPage(): string {
	return layout(
		'Not found',
		meta('name', 'robots', 'noindex'),
		`<main>
			<article>
				<div class="details">
					<p class="eyebrow">Otter</p>
					<h1>This link doesn't lead anywhere</h1>
					<p class="overview">It may have been cut off when it was copied.</p>
					<div class="actions"><a class="button" href="${projectUrl}">What is Otter?</a></div>
				</div>
			</article>
		</main>`,
	);
}

function meta(attribute: 'name' | 'property', key: string, value: string): string {
	return `<meta ${attribute}="${key}" content="${escapeHtml(value)}">`;
}

function shorten(text: string, maxLength: number): string {
	if (text.length <= maxLength) {
		return text;
	}
	return `${text.slice(0, maxLength - 1).trimEnd()}…`;
}

function layout(pageTitle: string, head: string, body: string): string {
	return `<!doctype html>
<html lang="en">
	<head>
		<meta charset="utf-8">
		<meta name="viewport" content="width=device-width, initial-scale=1">
		<meta name="theme-color" content="#0b0b0f">
		<title>${escapeHtml(pageTitle)} · Otter</title>
		${head}
		<style>${styles}</style>
	</head>
	<body>
		${body}
		<footer>
			<a href="${projectUrl}">Otter</a> · This product uses the TMDB API but is not endorsed or certified by TMDB.
		</footer>
	</body>
</html>`;
}

const styles = `
	* { box-sizing: border-box; }
	body { margin: 0; min-height: 100vh; display: flex; flex-direction: column; background: #0b0b0f; color: #fff;
		font-family: system-ui, -apple-system, "Segoe UI", sans-serif; }
	main { flex: 1; display: flex; align-items: center; justify-content: center; padding: 32px 20px; position: relative; overflow: hidden; }
	main::before { content: ""; position: absolute; inset: -40px; background: var(--backdrop, none) center / cover;
		filter: blur(40px) brightness(0.35); z-index: 0; }
	article { position: relative; z-index: 1; display: flex; gap: 28px; max-width: 760px; width: 100%; align-items: flex-start; }
	.poster { width: 200px; height: auto; border-radius: 14px; box-shadow: 0 20px 60px rgba(0, 0, 0, 0.6); flex-shrink: 0; }
	.details { display: flex; flex-direction: column; gap: 10px; }
	.eyebrow { margin: 0; font-size: 13px; letter-spacing: 0.04em; text-transform: uppercase; color: rgba(255, 255, 255, 0.55); }
	h1 { margin: 0; font-size: 30px; line-height: 1.15; }
	.subheading { margin: 0; font-size: 18px; color: rgba(255, 255, 255, 0.8); }
	.overview { margin: 4px 0 0; font-size: 15px; line-height: 1.55; color: rgba(255, 255, 255, 0.75); }
	.room-code { margin: 8px 0 0; font-size: 34px; font-weight: 700; letter-spacing: 0.12em; font-variant-numeric: tabular-nums; }
	.actions { display: flex; flex-wrap: wrap; gap: 10px; margin-top: 14px; }
	.button { padding: 12px 22px; border-radius: 999px; font-size: 15px; font-weight: 600; text-decoration: none;
		color: #fff; background: rgba(255, 255, 255, 0.1); border: 1px solid rgba(255, 255, 255, 0.18); }
	.button.primary { background: #fff; color: #000; border-color: #fff; }
	footer { padding: 16px 20px; font-size: 12px; text-align: center; color: rgba(255, 255, 255, 0.4); }
	footer a { color: inherit; }
	@media (max-width: 600px) {
		article { flex-direction: column; align-items: center; text-align: center; }
		.poster { width: 160px; }
		.actions { justify-content: center; }
	}
`;
