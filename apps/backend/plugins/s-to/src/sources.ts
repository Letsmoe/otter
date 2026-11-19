import type { PluginContext, SourceRetrieveParams } from "@letsmoe/otter-sdk";

/**
 * A stored play link ("/r?t=…") sits behind the site's captcha: the Otter app opens it,
 * passes the captcha (asking the user only when it can't hidden) and hands back the
 * hoster it redirects to, which the SDK's hosters turn into a media URL.
 */
export async function retrieveSource({ url }: SourceRetrieveParams, ctx: PluginContext) {
  const hosterUrl = await ctx.browser.open(url);
  ctx.log("play link led to", hosterUrl);
  return ctx.hosters.retrieve(hosterUrl, ctx);
}
