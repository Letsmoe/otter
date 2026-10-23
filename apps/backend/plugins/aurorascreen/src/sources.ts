import { PluginSourceError, type PluginContext, type SourceInput, type SourceRetrieveParams } from "@neoworks-dev/otter-sdk";
import { fetchStreamEntries, REFERER } from "./api.ts";
import { advertisedQuality, bestHlsEntry } from "./quality.ts";

/** Asks the lookup again, since the playable URL behind a stream link expires. */
export async function retrieveSource({ url }: SourceRetrieveParams, ctx: PluginContext): Promise<SourceInput> {
  const best = bestHlsEntry(await fetchStreamEntries(url, ctx));
  if (!best || !best.url) {
    throw new PluginSourceError("source_offline", `AuroraScreen: no HLS stream behind ${url}`);
  }
  return {
    url: best.url,
    mime_type: "application/x-mpegURL",
    headers: { Referer: REFERER },
    quality: advertisedQuality(best),
  };
}
