import type { PluginContext, SourceInput, SourceRetrieveParams } from "@neoworks-dev/otter-sdk";
import { HLS_MIME_TYPE } from "./constants.ts";
import { streamUrlsOf } from "./decrypt.ts";
import { fetchStreamEnvelope, openPlayerSession } from "./lookup.ts";
import { tokenizedServerUrl } from "./servers.ts";
import { targetOfLookupUrl } from "./target.ts";

/** Runs the whole chain at play time: the tokens are short-lived and bound to the caller's network. */
export async function retrieveSource({ url }: SourceRetrieveParams, ctx: PluginContext): Promise<SourceInput> {
  const session = await openPlayerSession(targetOfLookupUrl(url), ctx);
  const envelope = await fetchStreamEnvelope(session, ctx);
  const streamUrls = await streamUrlsOf(envelope, ctx);
  const playableUrl = await tokenizedServerUrl(streamUrls, session.playerOrigin, ctx);
  return {
    url: playableUrl,
    mime_type: HLS_MIME_TYPE,
    headers: { Referer: `${session.playerOrigin}/`, Origin: session.playerOrigin },
  };
}
