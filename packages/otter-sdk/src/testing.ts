// Helpers for testing plugins without a network: `testPlugin` runs a
// definition's handlers with a real context, `fixtures` serves saved pages.

import { existsSync, readFileSync } from "node:fs";
import { join } from "node:path";
import type { PluginContext, PluginDefinition } from "./definition.ts";
import type {
  EpisodeRef,
  List,
  MovieRef,
  Source,
  SourceRetrieveParams,
  Stream,
  StreamListParams,
  TitleFields,
} from "./model.ts";
import { PluginRuntime } from "./runtime.ts";

export interface TestPluginOptions {
  /** Replaces the network, typically `fixtures(dir)`. */
  fetch?: typeof fetch;
  /** The plugin's env: settings values and OTTER_BASE_URL. Empty by default. */
  env?: Record<string, string | undefined>;
}

export interface TestPlugin {
  ctx: PluginContext;
  streams: { list(params: StreamListParams): Promise<List<Stream>> };
  sources: { retrieve(params: SourceRetrieveParams): Promise<Source> };
}

/** Calls the plugin's handlers the way the backend would, and returns the completed objects. */
export function testPlugin(definition: PluginDefinition, options: TestPluginOptions = {}): TestPlugin {
  const runtime = new PluginRuntime(definition, { fetch: options.fetch, env: options.env || {} });
  return {
    ctx: runtime.ctx,
    streams: { list: (params) => runtime.listStreams(params) },
    sources: { retrieve: (params) => runtime.retrieveSource(params) },
  };
}

function identityPart(fields: TitleFields): string {
  if (fields.tmdb_id) return String(fields.tmdb_id);
  return fields.title.toLowerCase().replace(/[^a-z0-9]+/g, "-");
}

/** A movie to look for: `movie({ title: "Matrix", year: 1999, tmdb_id: 603 })`. */
export function movie(fields: TitleFields): MovieRef {
  return { object: "movie", id: `movie:${identityPart(fields)}`, ...fields };
}

export interface SeriesFields extends Omit<TitleFields, "title"> {
  /** The series' title. */
  series: string;
}

/** An episode to look for: `episode({ series: "Dark", tmdb_id: 70523 }, 1, 2)`. */
export function episode(fields: SeriesFields, seasonNumber: number, episodeNumber: number): EpisodeRef {
  const { series, ...rest } = fields;
  const seriesFields: TitleFields = { title: series, ...rest };
  return {
    object: "episode",
    id: `episode:${identityPart(seriesFields)}:${seasonNumber}:${episodeNumber}`,
    series: { object: "series", id: `series:${identityPart(seriesFields)}`, ...seriesFields },
    season_number: seasonNumber,
    episode_number: episodeNumber,
  };
}

const FIXTURE_EXTENSIONS = ["", ".html", ".json", ".js", ".txt"];

/** Maps `https://s.to/serie/dark?x=1` to `s.to_serie_dark_x_1`: host, path and query with every other character as "_". */
export function fixtureName(url: string, includeQuery: boolean): string {
  const parsed = new URL(url);
  let name = parsed.host + parsed.pathname;
  if (includeQuery) name += parsed.search;
  return name.replace(/[^A-Za-z0-9.-]+/g, "_").replace(/_+$/, "");
}

function findFixtureFile(directory: string, url: string, method: string): string | undefined {
  const prefix = method === "GET" ? "" : `${method.toLowerCase()}.`;
  const names = [fixtureName(url, true), fixtureName(url, false)];
  for (const name of names) {
    for (const extension of FIXTURE_EXTENSIONS) {
      const path = join(directory, prefix + name + extension);
      if (existsSync(path)) return path;
    }
  }
  return undefined;
}

function contentTypeOf(path: string): string {
  if (path.endsWith(".json")) return "application/json";
  return "text/html";
}

/**
 * A fetch replacement serving saved files. A request maps to the file named
 * after its URL (see `fixtureName`), looked up first with the query string, then
 * without, with or without an .html/.json/.js/.txt extension. Non-GET requests
 * look for the same name prefixed with the lowercase method ("post."). Anything
 * else answers 404.
 */
export function fixtures(directory: string): typeof fetch {
  return (async (input: RequestInfo | URL, init?: RequestInit) => {
    const url = String(input);
    const method = (init?.method || "GET").toUpperCase();
    const path = findFixtureFile(directory, url, method);
    if (!path) {
      console.error(`fixtures: nothing for ${method} ${url}; expected ${fixtureName(url, true)} in ${directory}`);
      return new Response("fixture not found", { status: 404 });
    }
    const headers = { "content-type": contentTypeOf(path) };
    return new Response(readFileSync(path), { status: 200, headers });
  }) as typeof fetch;
}
