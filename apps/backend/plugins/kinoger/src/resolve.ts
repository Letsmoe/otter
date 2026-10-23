import { PluginSourceError, type PluginContext, type SourceInput, type SourceRetrieveParams } from "@neoworks-dev/otter-sdk";
import type { ExtractedMedia } from "@neoworks-dev/otter-sdk";
import { voe } from "@neoworks-dev/otter-sdk/hosters";

const KINOGER_BASE_REFERER = "https://kinoger.com/";
const KINOGER_BE_REFERER = "https://kinoger.be/";
const HLS_MIME_TYPE = "application/x-mpegURL";

function preferHls(candidates: ExtractedMedia[]): ExtractedMedia | undefined {
  const hls = candidates.find((candidate) => candidate.mime_type === HLS_MIME_TYPE);
  if (hls) return hls;
  return candidates[0];
}

/** kinoger.be players are JW Player pages; `mediaUrls` also unpacks their packed scripts. */
async function retrieveKingerBe(url: string, ctx: PluginContext): Promise<SourceInput> {
  const page = await ctx.http.get(url, { referer: KINOGER_BASE_REFERER });
  const chosen = preferHls(ctx.extract.mediaUrls(await page.text()));
  if (!chosen) {
    throw new PluginSourceError("extraction_failed", `kinoger.be: no stream URL found for ${url}`);
  }
  return { url: chosen.url, mime_type: chosen.mime_type, headers: { Referer: KINOGER_BE_REFERER } };
}

/** kinoger.ru embeds are VOE player pages, so the SDK's VOE hoster handles them. */
export async function retrieveKinogerLink(params: SourceRetrieveParams, ctx: PluginContext): Promise<SourceInput> {
  const hostname = new URL(params.url).hostname;
  if (hostname === "kinoger.ru") return voe.retrieve(params.url, ctx);
  if (hostname === "kinoger.be") return retrieveKingerBe(params.url, ctx);
  throw new PluginSourceError("extraction_failed", `kinoger: unsupported domain ${hostname}`);
}
