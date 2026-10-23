import type { HTMLElement } from "node-html-parser";
import {
  HttpError,
  type MediaRef,
  type PluginContext,
  type StreamListParams,
  type TitleFields,
} from "@neoworks-dev/otter-sdk";
import {
  imdbIdOf,
  mirrorUrlsOf,
  parseSearchCards,
  searchTermsOf,
  seasonPathOf,
  type SearchCard,
} from "./site.ts";

// Search results per term that are checked against the title's IMDb id.
const MAX_CANDIDATES_PER_TERM = 5;

export async function listStreams({ media }: StreamListParams, ctx: PluginContext) {
  for (const term of searchTermsOf(...titlesOf(media))) {
    const mirrors = await mirrorsForTerm(term, media, ctx);
    if (mirrors) return ctx.list(mirrors.map((url) => ({ url })));
  }
  return ctx.list([]);
}

/** The titles to search for: the movie's, or the series' for an episode. */
function titlesOf(media: MediaRef): [string, string | undefined] {
  const fields = titleFieldsOf(media);
  return [fields.title, fields.original_title];
}

function titleFieldsOf(media: MediaRef): TitleFields {
  if (media.object === "movie") return media;
  return media.series;
}

/** The mirrors of the first search result that is the title looked for; undefined when none is. */
async function mirrorsForTerm(term: string, media: MediaRef, ctx: PluginContext): Promise<string[] | undefined> {
  const wantsSeries = media.object === "episode";
  const cards = (await searchCards(term, ctx)).filter((card) => card.isSeries === wantsSeries);
  for (const card of cards.slice(0, MAX_CANDIDATES_PER_TERM)) {
    const mirrors = await mirrorsOfCard(card, media, ctx);
    if (mirrors) return mirrors;
  }
  return undefined;
}

async function searchCards(term: string, ctx: PluginContext): Promise<SearchCard[]> {
  const root = await (await ctx.http.get(`/search?q=${encodeURIComponent(term)}`)).html();
  return parseSearchCards(root);
}

async function mirrorsOfCard(card: SearchCard, media: MediaRef, ctx: PluginContext): Promise<string[] | undefined> {
  const page = await fetchPage(pageOf(card, media), ctx);
  if (!page) return undefined;
  if (!isSameTitle(page, card, media, ctx)) return undefined;
  if (media.object === "movie") return mirrorUrlsOf(page);
  return episodeMirrorUrls(page, media.season_number, media.episode_number, ctx);
}

function pageOf(card: SearchCard, media: MediaRef): string {
  if (media.object === "movie") return card.path;
  return seasonPathOf(card.path, media.season_number);
}

/** A title the site does not have is not an error. */
async function fetchPage(path: string, ctx: PluginContext): Promise<HTMLElement | undefined> {
  try {
    return await (await ctx.http.get(path)).html();
  } catch (error) {
    if (error instanceof HttpError && error.status === 404) return undefined;
    throw error;
  }
}

/** The IMDb id on the page decides when both sides have one; otherwise the titles do. */
function isSameTitle(page: HTMLElement, card: SearchCard, media: MediaRef, ctx: PluginContext): boolean {
  const pageImdbId = imdbIdOf(page);
  const wantedImdbId = titleFieldsOf(media).imdb_id;
  if (pageImdbId && wantedImdbId) return pageImdbId === wantedImdbId;
  return ctx.match.titles(card.title, media);
}

/** Episode blocks carry an opaque base64 id that decodes to "tt<imdb>-s<season>e<episode>-...". */
function episodeMirrorUrls(page: HTMLElement, seasonNumber: number, episodeNumber: number, ctx: PluginContext): string[] {
  const block = page
    .querySelectorAll("[data-episode-id]")
    .find((candidate) => isEpisodeId(candidate.getAttribute("data-episode-id") || "", seasonNumber, episodeNumber, ctx));
  if (!block) return [];
  return mirrorUrlsOf(block);
}

function isEpisodeId(episodeId: string, seasonNumber: number, episodeNumber: number, ctx: PluginContext): boolean {
  const decoded = ctx.extract.base64(episodeId);
  return new RegExp(`-s${seasonNumber}e${episodeNumber}(?!\\d)`).test(decoded);
}
