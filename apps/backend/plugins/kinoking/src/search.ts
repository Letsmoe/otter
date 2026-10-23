import type { MovieRef, PluginContext } from "@neoworks-dev/otter-sdk";
import { SLOW_REQUEST_TIMEOUT_MS } from "./site.ts";

export interface SearchCard {
  internalId: string;
  type: string;
  tmdbId: number;
}

// Attributes of a result card, after whitespace is collapsed to single spaces.
const SEARCH_CARD_PATTERN = /data-id="(\d+)" data-type="(\w+)" data-tmdb="(\d+)" data-title="[^"]*"/g;

/** Result cards carry their TMDB id, so a card matches by id and no title comparison is needed. */
export function parseSearchCards(html: string): SearchCard[] {
  const flattened = html.replace(/\s+/g, " ");
  const pattern = new RegExp(SEARCH_CARD_PATTERN.source, "g");
  const cards: SearchCard[] = [];
  let match = pattern.exec(flattened);
  while (match) {
    cards.push({ internalId: match[1], type: match[2], tmdbId: Number(match[3]) });
    match = pattern.exec(flattened);
  }
  return cards;
}

function withoutLeadingArticle(title: string): string {
  return title.replace(/^(the|der|die|das)\s+/i, "");
}

/** The search is a substring match on the German title, so the leading article is tried dropped too. */
export function searchQueriesOf(media: MovieRef): string[] {
  const candidates = [
    media.title,
    withoutLeadingArticle(media.title),
    media.original_title,
    withoutLeadingArticle(media.original_title || ""),
  ];
  const queries: string[] = [];
  for (const candidate of candidates) {
    if (candidate && queries.indexOf(candidate) === -1) queries.push(candidate);
  }
  return queries;
}

/** The site's internal id of a movie, found by its TMDB id; undefined when the site does not have it. */
export async function findMovieInternalId(media: MovieRef, ctx: PluginContext): Promise<string | undefined> {
  if (media.tmdb_id === undefined) return undefined;
  for (const query of searchQueriesOf(media)) {
    const internalId = await searchForMovie(query, media.tmdb_id, ctx);
    if (internalId !== undefined) return internalId;
  }
  return undefined;
}

async function searchForMovie(query: string, tmdbId: number, ctx: PluginContext): Promise<string | undefined> {
  const response = await ctx.http.get(`/index.php?search=${encodeURIComponent(query)}`, {
    timeout_ms: SLOW_REQUEST_TIMEOUT_MS,
  });
  const card = parseSearchCards(await response.text()).find(
    (candidate) => candidate.type === "movie" && candidate.tmdbId === tmdbId,
  );
  if (!card) return undefined;
  return card.internalId;
}
