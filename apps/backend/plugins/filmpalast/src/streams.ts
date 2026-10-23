import type { MediaRef, PluginContext, StreamListParams, TitleFields } from "@neoworks-dev/otter-sdk";
import { linksOf } from "./detail.ts";
import { episodeStreamPath } from "./paths.ts";
import { searchCandidates, type SearchCandidate } from "./search.ts";

export async function listStreams({ media }: StreamListParams, ctx: PluginContext) {
  const streamPath = await locateStreamPath(media, ctx);
  if (!streamPath) return ctx.list([]);

  const detailRoot = await (await ctx.http.get(streamPath)).html();
  return ctx.list(linksOf(detailRoot));
}

async function locateStreamPath(media: MediaRef, ctx: PluginContext): Promise<string | undefined> {
  const candidates = await searchAll(searchQueriesOf(media), ctx);
  const wantedKind = mediaKindOf(media);
  const found = ctx.match.best(
    candidates.filter((candidate) => candidate.mediaKind === wantedKind),
    media,
  );
  if (!found) return undefined;
  if (media.object === "movie") return `/stream/${found.slug}`;
  return episodeStreamPath(found.slug, media.season_number, media.episode_number);
}

function mediaKindOf(media: MediaRef): SearchCandidate["mediaKind"] {
  if (media.object === "movie") return "movie";
  return "series";
}

/** Searches the German and the original title; both feed the same candidates. */
function searchQueriesOf(media: MediaRef): string[] {
  const fields = titleFieldsOf(media);
  const queries: string[] = [];
  for (const title of [fields.title, fields.original_title || ""]) {
    const trimmed = title.trim();
    if (trimmed && !queries.includes(trimmed)) queries.push(trimmed);
  }
  return queries;
}

function titleFieldsOf(media: MediaRef): TitleFields {
  if (media.object === "movie") return media;
  return media.series;
}

async function searchAll(queries: string[], ctx: PluginContext): Promise<SearchCandidate[]> {
  const candidates: SearchCandidate[] = [];
  for (const query of queries) {
    for (const candidate of await searchCandidates(query, ctx)) candidates.push(candidate);
  }
  return candidates;
}
