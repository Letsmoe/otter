import type { MediaRef } from "@neoworks-dev/otter-sdk";
import { API_BASE } from "./constants.ts";

/** The addon's lookup URL for a movie or episode; undefined when IMDb does not know it. */
export function lookupUrlOf(media: MediaRef): string | undefined {
  if (media.object === "movie") return movieUrlOf(media.imdb_id);
  return episodeUrlOf(media.series.imdb_id, media.season_number, media.episode_number);
}

function movieUrlOf(imdbId: string | undefined): string | undefined {
  if (!imdbId) return undefined;
  return `${API_BASE}/subtitles/movie/${imdbId}.json`;
}

function episodeUrlOf(imdbId: string | undefined, seasonNumber: number, episodeNumber: number): string | undefined {
  if (!imdbId) return undefined;
  return `${API_BASE}/subtitles/series/${imdbId}:${seasonNumber}:${episodeNumber}.json`;
}
