// Mirrors apps/backend/internal/plugins/types.go — keep in sync.
// All JSON field names use snake_case.
//
// Otter plugins do two things: find stream links for a title TMDB already
// identified ("streams"), and turn a hoster link into a playable media URL
// ("resolve").

export type Capability = "meta" | "streams" | "resolve";

export type MediaType = "movie" | "series" | "episode";

export interface PluginSettingField {
  key: string;
  label: string;
  description?: string;
  type: "string" | "password" | "boolean" | "number";
  required?: boolean;
  default?: string;
}

export interface PluginMeta {
  name: string;
  display_name?: string;
  description?: string;
  /** Emoji or URL */
  icon?: string;
  version: string;
  capabilities: Capability[];
  /** Domains this plugin handles for the resolve capability. Use "*" for wildcard. */
  domains?: string[];
  settings?: PluginSettingField[];
  /** Site sits behind a Cloudflare challenge and only works through FlareSolverr. */
  requires_solver?: boolean;
  /** The site's address; the backend offers it as an editable "Domain" setting. */
  base_url?: string;
  languages?: string[];
  media?: Array<"movie" | "episode">;
}

/** One playable source: usually a hoster page that still needs resolving. */
export interface Download {
  label: string;
  url: string;
  /** "4K" | "1080p" | "720p" | ... */
  quality?: string;
  language?: string;
  /** "mkv" | "mp4" | ... */
  format?: string;
  size_bytes?: number;
  speed_bps?: number;
}

/** Passed to the streams capability: a title already known from TMDB. */
export interface StreamsArgs {
  media_type: "movie" | "episode";
  title: string;
  original_title?: string;
  year?: number;
  tmdb_id?: number;
  imdb_id?: string;
  season_number?: number; // episode only
  episode_number?: number; // episode only
}

export interface StreamsResult {
  downloads: Download[];
}

export interface ResolveArgs {
  url: string;
}

export interface ResolveResult {
  url: string;
  mime_type: "application/x-mpegURL" | "application/dash+xml" | "video/mp4" | "video/webm";
  headers?: Record<string, string>;
  quality?: string;
  load_time_ms?: number;
  speed_bps?: number;
}

// ---------------------------------------------------------------------------
// Helpers for a plugin's own site search, used to locate a title before
// listing its links. Not part of the backend protocol.

export interface DiscoveredItem {
  title: string;
  media_type: MediaType;
  source_url: string;
  external_id: string;
  year?: number;
  poster_path?: string;
  tmdb_id?: number;
}

export interface SearchResult {
  items: DiscoveredItem[];
}
