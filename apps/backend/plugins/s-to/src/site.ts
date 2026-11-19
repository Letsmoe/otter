import type { HTMLElement } from "node-html-parser";

/** The site under its old and current name; its links are resolved by this plugin. */
export const SITE_DOMAINS = ["s.to", "serienstream.to"];

/** Which button of an episode page a stored link stands for. */
export interface LinkButton {
  provider: string;
  languageId: string;
}

/** The stored link: the episode page, with the button in the fragment ("…/episode-1#provider=VOE&language=1"). */
export function linkUrl(pageUrl: string, button: LinkButton): string {
  const fragment = new URLSearchParams({ provider: button.provider, language: button.languageId });
  return `${pageUrl}#${fragment}`;
}

/** The episode page and button of a stored link; undefined for anything else (e.g. old "/r?t=" links). */
export function parseLinkUrl(url: string): { pageUrl: string; button: LinkButton } | undefined {
  const [pageUrl, fragment] = url.split("#", 2);
  if (!fragment) return undefined;
  const params = new URLSearchParams(fragment);
  const provider = params.get("provider");
  if (!provider) return undefined;
  return { pageUrl, button: { provider, languageId: params.get("language") || "" } };
}

/** The CSS selector of a link's button on the episode page. */
export function buttonSelector(button: LinkButton): string {
  const provider = `[data-provider-name="${cssString(button.provider)}"]`;
  const language = `[data-language-id="${cssString(button.languageId)}"]`;
  return `button.link-box${provider}${language}`;
}

function cssString(value: string): string {
  return value.replace(/\\/g, "\\\\").replace(/"/g, '\\"');
}

// s.to numbers stream buttons by language id; unknown ids keep the site's own label.
const LANGUAGE_BY_ID: Record<string, string> = {
  "1": "de",
  "2": "en",
  "3": "de-sub",
};

export interface SeriesCandidate {
  title: string;
  /** "/serie/<slug>" */
  path: string;
}

export function parseSeriesList(root: HTMLElement): SeriesCandidate[] {
  const candidates: SeriesCandidate[] = [];
  for (const link of root.querySelectorAll("li.series-item > a")) {
    const path = link.getAttribute("href") || "";
    if (!path.startsWith("/serie/")) continue;
    candidates.push({ title: link.text.trim(), path });
  }
  return candidates;
}

export function episodePath(seriesPath: string, seasonNumber: number, episodeNumber: number): string {
  return `${seriesPath}/staffel-${seasonNumber}/episode-${episodeNumber}`;
}

export function languageOf(languageId: string, languageLabel: string): string {
  const mapped = LANGUAGE_BY_ID[languageId];
  if (mapped) return mapped;
  return languageLabel.toLowerCase();
}
