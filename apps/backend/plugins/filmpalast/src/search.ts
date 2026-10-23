import type { HTMLElement } from "node-html-parser";
import type { PluginContext } from "@neoworks-dev/otter-sdk";
import { isEpisodeSlug, seriesSlugOf, slugFromHref } from "./paths.ts";

export interface SearchCandidate {
  /** The movie's title, or the series' title for an episode page. */
  title: string;
  /** The stream slug; for an episode page, its series slug. */
  slug: string;
  mediaKind: "movie" | "series";
}

export async function searchCandidates(query: string, ctx: PluginContext): Promise<SearchCandidate[]> {
  const page = await ctx.http.get(`/search/title/${encodeURIComponent(query.trim())}`);
  const candidates: SearchCandidate[] = [];
  for (const link of (await page.html()).querySelectorAll('a[href*="/stream/"]')) {
    const candidate = candidateOf(link);
    if (candidate) candidates.push(candidate);
  }
  return candidates;
}

function candidateOf(link: HTMLElement): SearchCandidate | undefined {
  const slug = slugFromHref(link.getAttribute("href") || "");
  const rawTitle = titleOfLink(link);
  if (!slug || !rawTitle) return undefined;

  if (isEpisodeSlug(slug)) {
    return { title: seriesTitleOf(rawTitle), slug: seriesSlugOf(slug), mediaKind: "series" };
  }
  return { title: rawTitle, slug, mediaKind: "movie" };
}

function titleOfLink(link: HTMLElement): string {
  const titleAttribute = link.getAttribute("title");
  if (titleAttribute !== undefined) return titleAttribute;
  const imageAlt = link.querySelector("img")?.getAttribute("alt");
  if (imageAlt !== undefined) return imageAlt.replace(/^stream\s+/i, "");
  return link.text.trim();
}

/** "Dark Matter S01E13" -> "Dark Matter" */
function seriesTitleOf(episodeTitle: string): string {
  const withoutEpisodeMarker = episodeTitle.replace(/\s+S\d+E\d+.*/i, "").trim();
  if (withoutEpisodeMarker) return withoutEpisodeMarker;
  return episodeTitle;
}
