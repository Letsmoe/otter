import type { List, PluginContext, StreamInput, StreamListParams } from "@neoworks-dev/otter-sdk";
import { findMovieWatch } from "./api.ts";
import { qualityOf } from "./release.ts";

const LANGUAGE = "de";

export async function listStreams({ media }: StreamListParams, ctx: PluginContext): Promise<List<StreamInput>> {
  if (media.object !== "movie" || media.tmdb_id === undefined) return ctx.list([]);

  const watch = await findMovieWatch(media.tmdb_id, ctx);
  if (!watch || !watch.streamUrl) return ctx.list([]);
  return ctx.list([
    {
      url: watch.streamUrl,
      quality: qualityOf(watch.releaseName || ""),
      language: LANGUAGE,
    },
  ]);
}
