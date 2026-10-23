import type {
  EpisodeRef,
  List,
  MediaRef,
  MovieRef,
  PluginContext,
  StreamInput,
  StreamListParams,
} from "@neoworks-dev/otter-sdk";
import { fetchStreamEntries, lookupUrl, SERVER_LABELS, type LookupTarget } from "./api.ts";
import { advertisedQuality, bestHlsEntry } from "./quality.ts";

const LANGUAGE = "en";

interface ServerOutcome {
  stream?: StreamInput;
  error?: unknown;
}

export async function listStreams({ media }: StreamListParams, ctx: PluginContext): Promise<List<StreamInput>> {
  const target = targetOf(media);
  if (!target) return ctx.list([]);

  const outcomes = await Promise.all(
    Object.keys(SERVER_LABELS).map((server) => lookupServer(server, target, ctx)),
  );
  const streams = outcomes.map((outcome) => outcome.stream).filter(isStream);
  if (streams.length === 0) throwFirstFailure(outcomes);
  return ctx.list(streams);
}

function targetOf(media: MediaRef): LookupTarget | undefined {
  if (media.object === "movie") return movieTarget(media);
  return episodeTarget(media);
}

function movieTarget(media: MovieRef): LookupTarget | undefined {
  if (media.tmdb_id === undefined) return undefined;
  return { title: media.title, year: media.year, tmdbId: media.tmdb_id };
}

function episodeTarget(media: EpisodeRef): LookupTarget | undefined {
  const series = media.series;
  if (series.tmdb_id === undefined) return undefined;
  return {
    title: series.title,
    year: series.year,
    tmdbId: series.tmdb_id,
    season: media.season_number,
    episode: media.episode_number,
  };
}

/** One server failing must not hide the other's streams; only a total failure is an error. */
async function lookupServer(server: string, target: LookupTarget, ctx: PluginContext): Promise<ServerOutcome> {
  try {
    return { stream: await streamOf(server, target, ctx) };
  } catch (error) {
    ctx.log(`${server} lookup failed`, String(error));
    return { error };
  }
}

async function streamOf(server: string, target: LookupTarget, ctx: PluginContext): Promise<StreamInput | undefined> {
  const url = lookupUrl(server, target);
  const best = bestHlsEntry(await fetchStreamEntries(url, ctx));
  if (!best) return undefined;
  return {
    url,
    label: SERVER_LABELS[server],
    quality: advertisedQuality(best),
    language: LANGUAGE,
  };
}

function isStream(stream: StreamInput | undefined): stream is StreamInput {
  return stream !== undefined;
}

function throwFirstFailure(outcomes: ServerOutcome[]): void {
  const failure = outcomes.find((outcome) => outcome.error !== undefined);
  if (failure) throw failure.error;
}
