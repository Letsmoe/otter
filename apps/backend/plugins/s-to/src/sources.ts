import { PluginDeadLinkError, type PluginContext, type SourceRetrieveParams } from "@letsmoe/otter-sdk";
import { buttonSelector, parseLinkUrl } from "./site.ts";

// The form the episode page shows after a link's button: Turnstile and Altcha, posted into the player iframe.
const GATE_FORM = "#player-prepare-form";

/**
 * Plays a stored link: the Otter app opens the episode page, clicks the link's button and,
 * once the page's captcha is solved (hidden when it can, else by the user), posts the gate
 * form; the hoster the player iframe then loads goes to the SDK's hosters.
 */
export async function retrieveSource({ url }: SourceRetrieveParams, ctx: PluginContext) {
  const link = parseLinkUrl(url);
  if (!link) throw new PluginDeadLinkError(`${url} is an old play link; the episode's links are listed anew`);
  const hosterUrl = await ctx.browser.open(link.pageUrl, { click: buttonSelector(link.button), submit: GATE_FORM });
  ctx.log("link led to", hosterUrl);
  return ctx.hosters.retrieve(hosterUrl, ctx);
}
