import {
  type MediaRef,
  type PluginContext,
  type StreamInput,
  type StreamListParams,
} from "@neoworks-dev/otter-sdk";
import { chipStreamUrl, providerChipsOf, suggestedTitlesOf, yearOf, type ProviderChip, type SuggestedTitle } from "./site.ts";

// Suggestions per term whose movie pages are read for their year.
const MAX_CANDIDATES_PER_TERM = 3;

interface MoviePage {
  title: string;
  url: string;
  year?: number;
  chips: ProviderChip[];
}

/** Movies only: the site has no series, and no TMDB or IMDb ids to match on. */
export async function listStreams({ media }: StreamListParams, ctx: PluginContext) {
  if (media.object !== "movie") return ctx.list([]);
  for (const term of searchTermsOf(media)) {
    const page = await pageForTerm(term, media, ctx);
    if (page) return ctx.list(streamsOf(page));
  }
  return ctx.list([]);
}

function searchTermsOf(media: MediaRef): string[] {
  const candidates = [media.title, media.title.replace(/^(the|der|die|das)\s+/i, ""), media.original_title || ""];
  return candidates.filter((term, index) => term !== "" && candidates.indexOf(term) === index);
}

/** The page of the best-matching title (by year) among the suggestions for one search term. */
async function pageForTerm(term: string, media: MediaRef, ctx: PluginContext): Promise<MoviePage | undefined> {
  const suggestions = await suggest(term, ctx);
  const titled = suggestions.filter((suggestion) => ctx.match.titles(suggestion.title, media));
  const pages: MoviePage[] = [];
  for (const suggestion of titled.slice(0, MAX_CANDIDATES_PER_TERM)) {
    pages.push(await pageOf(suggestion, ctx));
  }
  return ctx.match.best(pages, media, { title: (page) => page.title, year: (page) => page.year });
}

async function suggest(term: string, ctx: PluginContext): Promise<SuggestedTitle[]> {
  const response = await ctx.http.get(`/search/suggest?q=${encodeURIComponent(term)}`, {
    headers: { Accept: "application/json" },
  });
  return suggestedTitlesOf(await response.json<{ movies?: SuggestedTitle[] }>());
}

async function pageOf(suggestion: SuggestedTitle, ctx: PluginContext): Promise<MoviePage> {
  const root = await (await ctx.http.get(suggestion.url)).html();
  return { title: suggestion.title, url: suggestion.url, year: yearOf(root), chips: providerChipsOf(root) };
}

/** One stream per provider chip; the link is resolved at play time, when the chip's token can be minted. */
function streamsOf(page: MoviePage): StreamInput[] {
  return page.chips
    .filter((chip) => chip.provider !== "")
    .map((chip) => ({ url: chipStreamUrl(page.url, chip.index), label: chip.provider, language: chip.language }));
}
