import type { MediaRef } from "@neoworks-dev/otter-sdk";
import { API_BASE } from "./constants.ts";

/** "tt0133093" to "0133093": the API wants the number padded to seven digits. */
function imdbNumberOf(imdbId: string): string {
  return imdbId.replace(/^tt/, "").padStart(7, "0");
}

/**
 * The API's search URL for a movie or episode in one language ("ger"), or
 * "all"; undefined when IMDb does not know the title. Path segments must stay
 * in alphabetical order or the API redirects.
 */
export function searchUrlOf(media: MediaRef, languageCode: string): string | undefined {
  if (media.object === "movie") return movieUrlOf(media.imdb_id, languageCode);
  const { series, season_number, episode_number } = media;
  return episodeUrlOf(series.imdb_id, season_number, episode_number, languageCode);
}

function movieUrlOf(imdbId: string | undefined, languageCode: string): string | undefined {
  if (!imdbId) return undefined;
  return `${API_BASE}/search/imdbid-${imdbNumberOf(imdbId)}/sublanguageid-${languageCode}`;
}

function episodeUrlOf(
  imdbId: string | undefined,
  seasonNumber: number,
  episodeNumber: number,
  languageCode: string,
): string | undefined {
  if (!imdbId) return undefined;
  const series = `imdbid-${imdbNumberOf(imdbId)}`;
  return `${API_BASE}/search/episode-${episodeNumber}/${series}/season-${seasonNumber}/sublanguageid-${languageCode}`;
}
