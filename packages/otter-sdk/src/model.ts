// The v2 plugin objects. Every object carries an `object` tag, ids are
// strings and field names are snake_case. The backend still speaks the v1
// wire format (types.ts); runtime.ts converts between the two.

import type { ResolveResult } from "./types.ts";

export type MediaKind = "movie" | "episode";

export type MimeType = ResolveResult["mime_type"];

export interface List<Item> {
  object: "list";
  data: Item[];
}

/** What a title is known by, for movies and for the series an episode belongs to. */
export interface TitleFields {
  title: string;
  original_title?: string;
  year?: number;
  tmdb_id?: number;
  imdb_id?: string;
}

export interface MovieRef extends TitleFields {
  object: "movie";
  /** "movie:<tmdb id>" */
  id: string;
}

export interface SeriesRef extends TitleFields {
  object: "series";
  /** "series:<tmdb id>" */
  id: string;
}

export interface EpisodeRef {
  object: "episode";
  /** "episode:<series tmdb id>:<season>:<episode>" */
  id: string;
  series: SeriesRef;
  season_number: number;
  episode_number: number;
}

export type MediaRef = MovieRef | EpisodeRef;

export interface StreamListParams {
  media: MediaRef;
}

export interface SourceRetrieveParams {
  url: string;
}

/** What `streams.list` returns per link. The SDK completes it into a `Stream`. */
export interface StreamInput {
  url: string;
  /** Defaults to the hoster's name, else the link's domain. */
  label?: string;
  /** "4K" | "1080p" | "720p" | ... */
  quality?: string;
  /** "de" | "en" | "de-sub" | ... */
  language?: string;
  /** "mkv" | "mp4" | ... */
  format?: string;
  size_bytes?: number;
}

export interface Stream extends StreamInput {
  object: "stream";
  label: string;
}

/** What `sources.retrieve` returns: a playable media URL. */
export interface SourceInput {
  url: string;
  mime_type: MimeType;
  headers?: Record<string, string>;
  quality?: string;
}

export interface Source extends SourceInput {
  object: "source";
}
