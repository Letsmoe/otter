import type { EpisodeRef, MediaRef, PluginContext } from "@neoworks-dev/otter-sdk";
import { PluginSourceError } from "@neoworks-dev/otter-sdk";

export type LookupTarget =
  | { kind: "movie"; tmdbId: number }
  | { kind: "tv"; tmdbId: number; season: number; episode: number };

/** The target of a movie or episode, or undefined when TMDB did not identify it. */
export function targetOf(media: MediaRef): LookupTarget | undefined {
  if (media.object === "movie") return movieTargetOf(media.tmdb_id);
  return episodeTargetOf(media);
}

function movieTargetOf(tmdbId: number | undefined): LookupTarget | undefined {
  if (tmdbId === undefined) return undefined;
  return { kind: "movie", tmdbId };
}

function episodeTargetOf(media: EpisodeRef): LookupTarget | undefined {
  const tmdbId = media.series.tmdb_id;
  if (tmdbId === undefined) return undefined;
  return { kind: "tv", tmdbId, season: media.season_number, episode: media.episode_number };
}

/** The vs_src lookup a stream links to; kept as the link because the playable URLs expire. */
export function lookupUrlOf(target: LookupTarget, ctx: PluginContext): string {
  const query = new URLSearchParams(queryParametersOf(target)).toString();
  return ctx.http.absolute(`/vs_src.php?${query}`);
}

function queryParametersOf(target: LookupTarget): Record<string, string> {
  if (target.kind === "movie") return { type: "movie", id: String(target.tmdbId) };
  return {
    type: "tv",
    id: String(target.tmdbId),
    season: String(target.season),
    episode: String(target.episode),
  };
}

export function targetOfLookupUrl(lookupUrl: string): LookupTarget {
  const parameters = new URL(lookupUrl).searchParams;
  const tmdbId = Number(parameters.get("id"));
  if (parameters.get("type") === "movie") return { kind: "movie", tmdbId };
  if (parameters.get("type") === "tv") {
    const season = Number(parameters.get("season"));
    const episode = Number(parameters.get("episode"));
    return { kind: "tv", tmdbId, season, episode };
  }
  throw new PluginSourceError("extraction_failed", `VidSrc: not a lookup link: ${lookupUrl}`);
}

/** The embed page the site expects as Referer for a lookup. */
export function embedPathOf(target: LookupTarget): string {
  if (target.kind === "movie") return `/embed/movie/${target.tmdbId}`;
  return `/embed/tv/${target.tmdbId}/${target.season}/${target.episode}`;
}
