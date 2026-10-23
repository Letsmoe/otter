import { PluginSourceError } from "../errors.ts";
import type { PluginContext } from "../definition.ts";
import type { SourceInput } from "../model.ts";
import { originOf } from "../url.ts";
import { assertFilePresent, getLinkPage } from "./dead-link.ts";
import { recaptchaToken } from "./recaptcha.ts";
import { defineHoster } from "./registry.ts";

// A missing file still gets the player page (200), with an error box instead of the video.
const MISSING_PLAYER_PAGE = /class="container error"[\s\S]{0,400}?Video not found/i;

interface FileUrlResponse {
  status?: string;
  token?: string;
}

function playerUrlOf(url: string): string {
  const mediaId = new URL(url).pathname.split("/").filter(Boolean).pop();
  return `${originOf(url)}/e/${mediaId}`;
}

async function retrieveVinovo(url: string, ctx: PluginContext): Promise<SourceInput> {
  const playerUrl = playerUrlOf(url);
  const origin = originOf(playerUrl);
  const mediaId = playerUrl.split("/").pop();

  const page = await getLinkPage(playerUrl, ctx, { referer: playerUrl });
  const html = await page.text();
  assertFilePresent(html, MISSING_PLAYER_PAGE, playerUrl);
  const pageToken = html.match(/name="token"\s*content="([^"]+)/);
  const streamBase = html.match(/<video[\s\S]+?data-base="([^"]+)/);
  if (!pageToken || !streamBase) {
    throw new PluginSourceError("extraction_failed", `Vinovo: no player data on ${playerUrl}`);
  }

  const response = await ctx.http.post(`${origin}/api/file/url/${mediaId}`, {
    body: { token: pageToken[1], recaptcha: await recaptchaToken(html, playerUrl, ctx) },
    headers: { Origin: origin, Referer: origin + "/", "X-Requested-With": "XMLHttpRequest" },
  });
  const data = await response.json<FileUrlResponse>();
  if (data.status !== "ok" || !data.token) {
    throw new PluginSourceError("extraction_failed", `Vinovo: file url refused for ${playerUrl}`);
  }
  return {
    url: `${streamBase[1]}/stream/${data.token}`,
    mime_type: "video/mp4",
    headers: { Referer: origin + "/" },
  };
}

export const vinovo = defineHoster({
  name: "Vinovo",
  domains: ["vinovo.to", "vinovo.si"],
  retrieve: retrieveVinovo,
});
