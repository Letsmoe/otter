import type { HTMLElement } from "node-html-parser";

export interface SearchCard {
  /** The title without the "[SERIE]" marker. */
  title: string;
  /** "/stream/<slug>" for movies, "/stream/<slug>?type=series" for series. */
  path: string;
  isSeries: boolean;
}

const SERIES_MARKER = /^\[SERIE\]\s*/;
const SERIES_TYPE = /[?&]type=(series|tv)\b/;
const IMDB_ID = /imdb\.com\/title\/(tt\d+)/;
const LOAD_MIRROR_URL = /loadMirror\('([^']+)'\)/;
const LEADING_ARTICLE = /^(the|der|die|das)\s+/i;

/** Each card's title link appears several times on the page; the first one per path wins. */
export function parseSearchCards(root: HTMLElement): SearchCard[] {
  const cards: SearchCard[] = [];
  for (const link of root.querySelectorAll('h2 a[href^="/stream/"]')) {
    const path = link.getAttribute("href") || "";
    if (cards.some((card) => card.path === path)) continue;
    const title = link.text.trim().replace(SERIES_MARKER, "");
    cards.push({ title, path, isSeries: SERIES_TYPE.test(path) });
  }
  return cards;
}

/** The season page of a series card: the card's path without its query, plus the season. */
export function seasonPathOf(cardPath: string, seasonNumber: number): string {
  const slugPath = cardPath.split("?")[0];
  return `${slugPath}?type=series&season=${seasonNumber}`;
}

export function imdbIdOf(root: HTMLElement): string | undefined {
  const match = root.toString().match(IMDB_ID);
  if (!match) return undefined;
  return match[1];
}

/** The hoster URLs of every `loadMirror('<url>')` link, in page order. */
export function mirrorUrlsOf(root: HTMLElement): string[] {
  const urls: string[] = [];
  for (const link of root.querySelectorAll('a[onclick^="return loadMirror("]')) {
    const url = urlFromOnclick(link.getAttribute("onclick") || "");
    if (url && !urls.includes(url)) urls.push(url);
  }
  return urls;
}

function urlFromOnclick(onclick: string): string | undefined {
  const match = onclick.match(LOAD_MIRROR_URL);
  if (!match) return undefined;
  return match[1];
}

/** Search terms to try, most literal first: the title, without a leading article, then the original title. */
export function searchTermsOf(title: string, originalTitle?: string): string[] {
  const candidates = [title, title.replace(LEADING_ARTICLE, ""), originalTitle || ""];
  return candidates.filter((term, index) => term !== "" && candidates.indexOf(term) === index);
}
