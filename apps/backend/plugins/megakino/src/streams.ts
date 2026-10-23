import type { HTMLElement } from "node-html-parser";
import {
  HttpError,
  type MediaRef,
  type PluginContext,
  type StreamListParams,
  type TitleFields,
} from "@neoworks-dev/otter-sdk";
import { sessionHeadersOf } from "./session.ts";
import {
  episodeHosterUrls,
  movieHosterUrls,
  ownTmdbIdsOf,
  parseSearchCards,
  searchTermsOf,
  type SearchCard,
} from "./site.ts";

// Search results per term that are checked against the title's TMDB id.
const MAX_CANDIDATES_PER_TERM = 5;

type Headers = Record<string, string>;

export async function listStreams({ media }: StreamListParams, ctx: PluginContext) {
  const headers = await sessionHeadersOf(ctx);
  const fields = titleFieldsOf(media);
  for (const term of searchTermsOf(fields.title, fields.original_title)) {
    const urls = await urlsForTerm(term, media, headers, ctx);
    if (urls) return ctx.list(urls.map((url) => ({ url })));
  }
  return ctx.list([]);
}

function titleFieldsOf(media: MediaRef): TitleFields {
  if (media.object === "movie") return media;
  return media.series;
}

/** The hosters of the first search result that is the title looked for; undefined when none is. */
async function urlsForTerm(term: string, media: MediaRef, headers: Headers, ctx: PluginContext): Promise<string[] | undefined> {
  const cards = (await searchCards(term, headers, ctx)).filter((card) => isCardOf(card, media));
  for (const card of cards.slice(0, MAX_CANDIDATES_PER_TERM)) {
    const urls = await urlsOfCard(card, media, headers, ctx);
    if (urls) return urls;
  }
  return undefined;
}

/** Movies are the cards without a season; episodes need the card of their season. */
function isCardOf(card: SearchCard, media: MediaRef): boolean {
  if (media.object === "movie") return card.season === undefined;
  return card.season === media.season_number;
}

async function searchCards(term: string, headers: Headers, ctx: PluginContext): Promise<SearchCard[]> {
  const body = { do: "search", subaction: "search", story: term };
  const root = await (await ctx.http.post("/index.php?do=search", { body, headers })).html();
  return parseSearchCards(root);
}

async function urlsOfCard(card: SearchCard, media: MediaRef, headers: Headers, ctx: PluginContext): Promise<string[] | undefined> {
  const page = await fetchPage(card.path, headers, ctx);
  if (!page) return undefined;
  if (!isSameTitle(page, card, media, ctx)) return undefined;
  if (media.object === "movie") return movieHosterUrls(page);
  return episodeHosterUrls(page, media.episode_number);
}

/** A title the site does not have is not an error. */
async function fetchPage(path: string, headers: Headers, ctx: PluginContext): Promise<HTMLElement | undefined> {
  try {
    return await (await ctx.http.get(path, { headers })).html();
  } catch (error) {
    if (error instanceof HttpError && error.status === 404) return undefined;
    throw error;
  }
}

/** The TMDB id in the page's images decides when the page has one; otherwise the titles do. */
function isSameTitle(page: HTMLElement, card: SearchCard, media: MediaRef, ctx: PluginContext): boolean {
  const pageTmdbIds = ownTmdbIdsOf(page);
  const wantedTmdbId = titleFieldsOf(media).tmdb_id;
  if (pageTmdbIds.length > 0 && wantedTmdbId) return pageTmdbIds.includes(wantedTmdbId);
  return ctx.match.titles(card.title, media);
}
