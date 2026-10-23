import { HttpError, type PluginContext } from "@neoworks-dev/otter-sdk";

export interface WatchInfo {
  /** Scene-style release name, e.g. "The.Matrix.1999.GERMAN.iNTERNAL.AC3.DVDRip.x264-UPRiSiNG". */
  releaseName?: string;
  /** The hoster link of the release. */
  streamUrl?: string;
}

/** Whether the site has the movie and, if so, its watch link; both are absent for a movie it does not have. */
export async function findMovieWatch(tmdbId: number, ctx: PluginContext): Promise<WatchInfo | undefined> {
  const detail = await getOrUndefined<unknown>(`/api/movies/${tmdbId}`, ctx);
  if (detail === undefined) return undefined;
  return getOrUndefined<WatchInfo>(`/api/movies/${tmdbId}/watch`, ctx);
}

async function getOrUndefined<Data>(path: string, ctx: PluginContext): Promise<Data | undefined> {
  try {
    return await (await ctx.http.get(path)).json<Data>();
  } catch (error) {
    if (error instanceof HttpError && error.status === 404) return undefined;
    throw error;
  }
}
