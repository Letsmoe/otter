import type { HTMLElement } from "node-html-parser";

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
