import type { HTMLElement } from "node-html-parser";

// aniworld numbers the language tabs of an episode page by data-lang-key.
const LANGUAGE_BY_KEY: Record<string, string> = {
  "1": "de",
  "2": "en-sub",
  "3": "de-sub",
};

const LANGUAGE_LABEL_BY_KEY: Record<string, string> = {
  "1": "Deutsch",
  "2": "Untertitel Englisch",
  "3": "Untertitel Deutsch",
};

const NAMED_ENTITIES: Record<string, string> = {
  "&amp;": "&",
  "&quot;": '"',
  "&lt;": "<",
  "&gt;": ">",
};

export interface SeriesCandidate {
  title: string;
  /** "/anime/stream/<slug>" */
  path: string;
}

/** One candidate per known title of each anime: the list title first, then every alternative title. */
export function parseSeriesList(root: HTMLElement): SeriesCandidate[] {
  const candidates: SeriesCandidate[] = [];
  for (const link of root.querySelectorAll('a[data-alternative-title][href^="/anime/stream/"]')) {
    const path = link.getAttribute("href") || "";
    candidates.push({ title: link.text.trim(), path });
    for (const alternativeTitle of alternativeTitlesOf(link)) {
      candidates.push({ title: alternativeTitle, path });
    }
  }
  return candidates;
}

/** The attribute is a comma-separated list; titles containing commas split into extra candidates, which only ever add matches. */
function alternativeTitlesOf(link: HTMLElement): string[] {
  const raw = decodeEntities(link.getAttribute("data-alternative-title") || "");
  return raw
    .split(",")
    .map((title) => title.trim())
    .filter((title) => title !== "");
}

function decodeEntities(text: string): string {
  return text
    .replace(/&#0*39;/g, "'")
    .replace(/&amp;|&quot;|&lt;|&gt;/g, (entity) => NAMED_ENTITIES[entity]);
}

export function episodePath(seriesPath: string, seasonNumber: number, episodeNumber: number): string {
  return `${seriesPath}/staffel-${seasonNumber}/episode-${episodeNumber}`;
}

export function languageOf(languageKey: string): string {
  const mapped = LANGUAGE_BY_KEY[languageKey];
  if (mapped) return mapped;
  return "";
}

export function languageLabelOf(languageKey: string): string {
  return LANGUAGE_LABEL_BY_KEY[languageKey] || "";
}

/** "VOE" from the hoster icon's title "Hoster VOE". */
export function hosterNameOf(linkItem: HTMLElement): string {
  const iconTitle = linkItem.querySelector("i.icon")?.getAttribute("title") || "";
  return iconTitle.replace(/^Hoster\s+/, "").trim();
}
