import { PluginSourceError } from "../errors.ts";
import type { PluginContext } from "../definition.ts";
import type { SourceInput } from "../model.ts";
import { originOf, resolveAgainst } from "../url.ts";
import { assertFilePresent, getLinkPage } from "./dead-link.ts";
import { defineHoster } from "./registry.ts";

const TOKEN_CHARACTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
const TOKEN_LENGTH = 10;

// The player script: dsplayer.hotkeys[...]'<pass_md5 path>' ... function makePlay ... return ...?token=<token>&expiry=
const PLAYER_SCRIPT = /dsplayer\.hotkeys[^']+'([^']+)[\s\S]+?function\s*makePlay[\s\S]+?return[^?]+([^"]+)/;

// The player of a missing video is a 200 page titled "Video not found" (or "File not found" on /w/ links).
const MISSING_PLAYER_PAGE = /<title>\s*(Video|File) not found/i;

function randomSuffix(): string {
  let suffix = "";
  for (let index = 0; index < TOKEN_LENGTH; index++) {
    suffix += TOKEN_CHARACTERS[Math.floor(Math.random() * TOKEN_CHARACTERS.length)];
  }
  return suffix;
}

/** The /d/ landing page either embeds the player in an iframe or has a twin at /e/. */
async function loadPlayerHtml(landingUrl: string, ctx: PluginContext): Promise<string> {
  const landing = await getLinkPage(landingUrl, ctx, { referer: originOf(landingUrl) + "/" });
  const finalLandingUrl = landing.url || landingUrl;
  const landingHtml = await landing.text();
  const frame = landingHtml.match(/<iframe\s*src="([^"]+)/);
  let playerUrl = finalLandingUrl.replace("/d/", "/e/");
  if (frame) playerUrl = resolveAgainst(finalLandingUrl, frame[1]);
  const player = await getLinkPage(playerUrl, ctx, { referer: finalLandingUrl });
  const playerHtml = await player.text();
  assertFilePresent(playerHtml, MISSING_PLAYER_PAGE, playerUrl);
  return playerHtml;
}

async function retrieveDood(url: string, ctx: PluginContext): Promise<SourceInput> {
  const landingUrl = url.replace("/e/", "/d/");
  const html = await loadPlayerHtml(landingUrl, ctx);
  const script = html.match(PLAYER_SCRIPT);
  if (!script) {
    throw new PluginSourceError("extraction_failed", `Dood: no player script on ${url}`);
  }

  const origin = originOf(landingUrl);
  const passResponse = await ctx.http.get(resolveAgainst(origin + "/", script[1]), { referer: origin + "/" });
  const passBody = (await passResponse.text()).trim();
  const headers = { Referer: origin + "/" };
  if (passBody.includes("cloudflarestorage.")) {
    return { url: passBody, mime_type: "video/mp4", headers };
  }
  return { url: passBody + randomSuffix() + script[2] + Date.now(), mime_type: "video/mp4", headers };
}

// Mirrors of DoodStream, from the ResolveURL project's list; they come and go.
export const dood = defineHoster({
  name: "Doodstream",
  domains: [
    "dood.watch", "doodstream.com", "dood.to", "dood.so", "dood.cx", "dood.la", "dood.ws",
    "dood.sh", "doodstream.co", "dood.pm", "dood.wf", "dood.re", "dood.yt", "dooood.com",
    "dood.stream", "ds2play.com", "doods.pro", "ds2video.com", "d0o0d.com", "do0od.com",
    "d0000d.com", "d000d.com", "dood.li", "dood.work", "dooodster.com", "vidply.com",
    "all3do.com", "do7go.com", "doodcdn.io", "doply.net", "vide0.net", "vvide0.com",
    "d-s.io", "dsvplay.com", "myvidplay.com", "playmogo.com",
  ],
  retrieve: retrieveDood,
});
