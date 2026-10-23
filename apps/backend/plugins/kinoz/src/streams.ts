import type { HTMLElement } from "node-html-parser";
import type {
  EpisodeRef,
  MediaRef,
  MovieRef,
  PluginContext,
  StreamInput,
  StreamListParams,
  TitleFields,
} from "@neoworks-dev/otter-sdk";
import { episodeMirrorPath, mirrorPath, mirrorsByEpisodePath } from "./paths.ts";
import { searchCandidates, type SearchCandidate } from "./search.ts";
import { episodeListed, seasonSelectionOf } from "./seasons.ts";

export async function listStreams({ media }: StreamListParams, ctx: PluginContext) {
  if (media.object === "episode") return ctx.list(await episodeStreams(media, ctx));
  return ctx.list(await movieStreams(media, ctx));
}

async function movieStreams(media: MovieRef, ctx: PluginContext): Promise<StreamInput[]> {
  const candidate = ctx.match.best(await searchResults(media, ctx), media);
  if (!candidate) return [];

  const detailRoot = await (await ctx.http.get(candidate.sourcePath)).html();
  return dedupe(
    hostersOf(detailRoot).map((hoster) => ({
      url: ctx.http.absolute(mirrorPath(candidate.slug, hoster.id)),
      label: hoster.name,
    })),
  );
}

/**
 * kinoz lists some series several times under the same title, each entry with
 * other seasons, so the first matching entry that has the episode wins.
 */
async function episodeStreams(media: EpisodeRef, ctx: PluginContext): Promise<StreamInput[]> {
  const candidates = (await searchResults(media, ctx)).filter((candidate) => ctx.match.titles(candidate.title, media));
  for (const candidate of candidates) {
    const hosters = await episodeHostersOf(candidate, media, ctx);
    if (hosters.length === 0) continue;
    return dedupe(
      hosters.map((hoster) => ({
        url: ctx.http.absolute(episodeMirrorPath(candidate.slug, hoster.id, media.season_number, media.episode_number)),
        label: hoster.name,
      })),
    );
  }
  return [];
}

/** The hosters of one episode; the detail page only shows those of the preselected episode. */
async function episodeHostersOf(candidate: SearchCandidate, media: EpisodeRef, ctx: PluginContext): Promise<Hoster[]> {
  const detailRoot = await (await ctx.http.get(candidate.sourcePath)).html();
  const seasons = seasonSelectionOf(detailRoot);
  if (!seasons) return [];
  if (!episodeListed(seasons, media.season_number, media.episode_number)) return [];

  const path = mirrorsByEpisodePath(seasons.seriesQuery, media.season_number, media.episode_number);
  const mirrorsRoot = await (await ctx.http.get(path)).html();
  return hostersOf(mirrorsRoot);
}

/** Search results for the German and the original title, in that order. */
async function searchResults(media: MediaRef, ctx: PluginContext): Promise<SearchCandidate[]> {
  const results: SearchCandidate[] = [];
  const seenSlugs = new Set<string>();
  for (const query of searchQueriesOf(media)) {
    for (const candidate of await searchCandidates(query, ctx)) {
      if (seenSlugs.has(candidate.slug)) continue;
      seenSlugs.add(candidate.slug);
      results.push(candidate);
    }
  }
  return results;
}

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

function dedupe(streams: StreamInput[]): StreamInput[] {
  const seenUrls = new Set<string>();
  const unique: StreamInput[] = [];
  for (const stream of streams) {
    if (seenUrls.has(stream.url)) continue;
    seenUrls.add(stream.url);
    unique.push(stream);
  }
  return unique;
}

interface Hoster {
  id: string;
  name: string;
}

function hostersOf(root: HTMLElement): Hoster[] {
  const hosters: Hoster[] = [];
  for (const item of root.querySelectorAll("li.MirBtn")) {
    const hoster = hosterOf(item);
    if (hoster) hosters.push(hoster);
  }
  return hosters;
}

function hosterOf(item: HTMLElement): Hoster | undefined {
  const idMatch = (item.getAttribute("rel") || "").match(/Hoster=(\d+)/);
  const name = (item.querySelector(".Named")?.text || "").trim();
  if (!idMatch || !name) return undefined;
  return { id: idMatch[1], name };
}
