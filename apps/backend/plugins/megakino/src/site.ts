import type { HTMLElement } from "node-html-parser";

export interface SearchCard {
  title: string;
  /** "/films/<id>-<slug>.html" or "/<section>/<id>-<slug>-staffel-<n>.html" */
  path: string;
  /** Set on season cards only. */
  season?: number;
}

const SEASON_SUFFIX = /-staffel-(\d+)\.html$/;
const SEASON_TITLE_SUFFIX = /\s*-\s*Staffel\s*\d+\s*$/i;
const TMDB_IMAGE = /(\d+)_(?:poster|backdrop_path)/;
const LEADING_ARTICLE = /^(the|der|die|das)\s+/i;

export function parseSearchCards(root: HTMLElement): SearchCard[] {
  return root.querySelectorAll("a.poster").map((link) => cardOf(link));
}

function cardOf(link: HTMLElement): SearchCard {
  const path = link.getAttribute("href") || "";
  const title = link.querySelector("h3.poster__title")?.text.trim() || "";
  const seasonMatch = path.match(SEASON_SUFFIX);
  const card: SearchCard = { title: title.replace(SEASON_TITLE_SUFFIX, ""), path };
  if (seasonMatch) card.season = Number(seasonMatch[1]);
  return card;
}

/** TMDB ids in the page's own images: the poster, the trailer backdrop. Related titles are not included. */
export function ownTmdbIdsOf(root: HTMLElement): number[] {
  const imageUrls = [
    ...attributeValuesOf(root, 'meta[property="og:image"]', "content"),
    ...attributeValuesOf(root, 'img[itemprop="image"]', "data-src"),
    ...attributeValuesOf(root, ".pmovie__btn-trailer img", "data-src"),
  ];
  const ids = imageUrls.map(tmdbIdOf).filter((id): id is number => id !== undefined);
  return ids;
}

function attributeValuesOf(root: HTMLElement, selector: string, attribute: string): string[] {
  return root.querySelectorAll(selector).map((element) => element.getAttribute(attribute) || "");
}

function tmdbIdOf(imageUrl: string): number | undefined {
  const match = imageUrl.match(TMDB_IMAGE);
  if (!match) return undefined;
  return Number(match[1]);
}

/** The hoster of a movie page: the player iframe's source. */
export function movieHosterUrls(root: HTMLElement): string[] {
  const source = root.querySelector("iframe#film_main")?.getAttribute("data-src") || "";
  if (!source) return [];
  return [source];
}

/** The hosters of one episode: the options of its hoster select, `select#ep<n>`. */
export function episodeHosterUrls(root: HTMLElement, episodeNumber: number): string[] {
  const options = root.querySelectorAll(`select#ep${episodeNumber} option`);
  return options.map((option) => option.getAttribute("value") || "").filter((url) => url.startsWith("https://"));
}

/** Search terms to try, most literal first: the title, without a leading article, then the original title. */
export function searchTermsOf(title: string, originalTitle?: string): string[] {
  const candidates = [title, title.replace(LEADING_ARTICLE, ""), originalTitle || ""];
  return candidates.filter((term, index) => term !== "" && candidates.indexOf(term) === index);
}
