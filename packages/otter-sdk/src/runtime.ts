// Bridges a v2 plugin definition and the v1 wire protocol the backend speaks.

import { createContext, type ContextOptions } from "./context.ts";
import type { PluginContext, PluginDefinition } from "./definition.ts";
import { domainOf } from "./http.ts";
import type {
  EpisodeRef,
  List,
  MediaRef,
  MovieRef,
  SeriesRef,
  Source,
  SourceInput,
  SourceRetrieveParams,
  Stream,
  StreamInput,
  StreamListParams,
} from "./model.ts";
import { normalize } from "./match.ts";
import { resolveWithTiming } from "./timing.ts";
import type { Capability, Download, PluginMeta, ResolveResult, StreamsArgs, StreamsResult } from "./types.ts";

export interface PluginHandlers {
  meta: PluginMeta;
  /** Locates a TMDB-known movie or episode on the site and lists its links. */
  streams?: (args: StreamsArgs) => Promise<StreamsResult>;
  /** Turns a hoster link into a playable media URL. */
  resolve?: (url: string) => Promise<ResolveResult>;
}

export function isPluginDefinition(plugin: PluginHandlers | PluginDefinition): plugin is PluginDefinition {
  return !("meta" in plugin);
}

// ---- meta ------------------------------------------------------------------

function capabilitiesOf(definition: PluginDefinition): Capability[] {
  const capabilities: Capability[] = [];
  if (definition.streams) capabilities.push("streams");
  if (definition.sources) capabilities.push("resolve");
  return capabilities;
}

function domainsOf(definition: PluginDefinition): string[] | undefined {
  if (!definition.sources) return undefined;
  const domains = [...definition.sources.domains];
  if (definition.sources.fallback) domains.push("*");
  return domains;
}

export function metaOf(definition: PluginDefinition): PluginMeta {
  return {
    name: definition.id,
    display_name: definition.name,
    description: definition.description,
    icon: definition.icon,
    version: definition.version,
    capabilities: capabilitiesOf(definition),
    domains: domainsOf(definition),
    settings: definition.settings,
    requires_solver: definition.requires_solver,
    base_url: definition.base_url,
    languages: definition.languages,
    media: definition.media,
  };
}

// ---- params ----------------------------------------------------------------

/** "603" if the TMDB id is known, else the IMDb id, else the normalized title. */
function identityPart(args: StreamsArgs): string {
  if (args.tmdb_id) return String(args.tmdb_id);
  if (args.imdb_id) return args.imdb_id;
  return normalize(args.title).replace(/ /g, "-");
}

function movieRefOf(args: StreamsArgs): MovieRef {
  return {
    object: "movie",
    id: `movie:${identityPart(args)}`,
    title: args.title,
    original_title: args.original_title,
    year: args.year,
    tmdb_id: args.tmdb_id,
    imdb_id: args.imdb_id,
  };
}

function seriesRefOf(args: StreamsArgs): SeriesRef {
  return {
    object: "series",
    id: `series:${identityPart(args)}`,
    title: args.title,
    original_title: args.original_title,
    year: args.year,
    tmdb_id: args.tmdb_id,
    imdb_id: args.imdb_id,
  };
}

function episodeRefOf(args: StreamsArgs): EpisodeRef {
  const seasonNumber = args.season_number || 0;
  const episodeNumber = args.episode_number || 0;
  return {
    object: "episode",
    id: `episode:${identityPart(args)}:${seasonNumber}:${episodeNumber}`,
    series: seriesRefOf(args),
    season_number: seasonNumber,
    episode_number: episodeNumber,
  };
}

/** The backend's StreamsArgs describe an episode by its series; this turns them into the v2 media ref. */
export function toStreamListParams(args: StreamsArgs): StreamListParams {
  let media: MediaRef = movieRefOf(args);
  if (args.media_type === "episode") media = episodeRefOf(args);
  return { media };
}

// ---- results ---------------------------------------------------------------

function labelOf(input: StreamInput, ctx: PluginContext): string {
  if (input.label) return input.label;
  const hosterName = ctx.hosters.nameOf(input.url);
  if (hosterName) return hosterName;
  return domainOf(input.url) || "Stream";
}

export function completeStream(input: StreamInput, ctx: PluginContext): Stream {
  return { ...input, object: "stream", label: labelOf(input, ctx), language: languageOf(input, ctx) };
}

/** A site with a single declared language serves its links in it, unless the link says otherwise. */
function languageOf(input: StreamInput, ctx: PluginContext): string | undefined {
  if (input.language) return input.language;
  if (ctx.plugin.languages.length !== 1) return undefined;
  return ctx.plugin.languages[0];
}

export function toDownload(stream: Stream): Download {
  return {
    label: stream.label,
    url: stream.url,
    quality: stream.quality,
    language: stream.language,
    format: stream.format,
    size_bytes: stream.size_bytes,
  };
}

export function completeSource(input: SourceInput): Source {
  return { ...input, object: "source" };
}

export function toResolveResult(source: Source): ResolveResult {
  return {
    url: source.url,
    mime_type: source.mime_type,
    headers: source.headers,
    quality: source.quality,
  };
}

// ---- running ---------------------------------------------------------------

/** A definition's capabilities, run with a real context. */
export class PluginRuntime {
  readonly ctx: PluginContext;

  constructor(
    private readonly definition: PluginDefinition,
    options: ContextOptions = {},
  ) {
    this.ctx = createContext(definition, options);
  }

  async listStreams(params: StreamListParams): Promise<List<Stream>> {
    const capability = this.definition.streams;
    if (!capability) throw new Error("streams not implemented");
    const result = await capability.list(params, this.ctx);
    return this.ctx.list(result.data.map((input) => completeStream(input, this.ctx)));
  }

  async retrieveSource(params: SourceRetrieveParams): Promise<Source> {
    const capability = this.definition.sources;
    if (!capability) throw new Error("resolve not implemented");
    return completeSource(await capability.retrieve(params, this.ctx));
  }
}

/** The v1 handlers of a v2 definition, so one dispatcher serves both kinds of plugin. */
export function handlersOf(definition: PluginDefinition, options: ContextOptions = {}): PluginHandlers {
  const runtime = new PluginRuntime(definition, options);
  const handlers: PluginHandlers = { meta: metaOf(definition) };
  if (definition.streams) {
    handlers.streams = async (args) => {
      const list = await runtime.listStreams(toStreamListParams(args));
      return { downloads: list.data.map(toDownload) };
    };
  }
  if (definition.sources) {
    handlers.resolve = (url) => resolveWithTiming(async () => toResolveResult(await runtime.retrieveSource({ url })));
  }
  return handlers;
}
