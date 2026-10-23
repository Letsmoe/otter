import type {
  EpisodeRef,
  List,
  MovieRef,
  PluginContext,
  StreamInput,
  StreamListParams,
} from "@neoworks-dev/otter-sdk";
import { findMovieInternalId } from "./search.ts";
import { parseEpisodeGroups, parseMovieGroups, streamsOf, type EpisodeGroupEntry } from "./servers.ts";
import { SLOW_REQUEST_TIMEOUT_MS } from "./site.ts";

export async function listStreams({ media }: StreamListParams, ctx: PluginContext): Promise<List<StreamInput>> {
  if (media.object === "movie") return ctx.list(await movieStreams(media, ctx));
  return ctx.list(await episodeStreams(media, ctx));
}

async function movieStreams(media: MovieRef, ctx: PluginContext): Promise<StreamInput[]> {
  const internalId = await findMovieInternalId(media, ctx);
  if (internalId === undefined) return [];
  const page = await ctx.http.get(`/movie.php?id=${internalId}`, { timeout_ms: SLOW_REQUEST_TIMEOUT_MS });
  return streamsOf(parseMovieGroups(await page.text()));
}

/** The site looks episodes up by TMDB id directly, so no search is needed. */
async function episodeStreams(media: EpisodeRef, ctx: PluginContext): Promise<StreamInput[]> {
  const tmdbId = media.series.tmdb_id;
  if (tmdbId === undefined) return [];
  const path = `/series.php?ajax_huhu_links=1&tmdb=${tmdbId}&s=${media.season_number}&e=${media.episode_number}`;
  // Two slow attempts still fit the backend's lookup budget; a third would not.
  const response = await ctx.http.get(path, { timeout_ms: SLOW_REQUEST_TIMEOUT_MS, retries: 1 });
  return streamsOf(parseEpisodeGroups(await response.json<EpisodeGroupEntry[]>()));
}
