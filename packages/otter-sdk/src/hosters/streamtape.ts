import { PluginSourceError } from "../errors.ts";
import type { PluginContext } from "../definition.ts";
import type { SourceInput } from "../model.ts";
import { originOf, resolveAgainst } from "../url.ts";
import { getLinkPage } from "./dead-link.ts";
import { defineHoster } from "./registry.ts";

// The page assigns the link in pieces to a hidden element:
//   document.getElementById('x').innerHTML = "//host/get_video?id=" + ('abcxyz').substring(2) + ...
const LINK_ASSIGNMENT = /ById\('.+?=\s*(["']\/\/[^;<]+)/g;

/** Joins the quoted pieces of the assignment, applying each `.substring(n)` to its piece. */
export function assembleLink(assignment: string): string {
  let link = "";
  for (const piece of assignment.replace(/'/g, '"').split("+")) {
    const text = piece.match(/"([^"]*)/);
    if (!text) continue;
    let skipped = 0;
    for (const substring of piece.matchAll(/substring\((\d+)/g)) {
      skipped += parseInt(substring[1], 10);
    }
    link += text[1].slice(skipped);
  }
  return link;
}

function lastAssignment(html: string): string | undefined {
  const assignments = Array.from(html.matchAll(LINK_ASSIGNMENT));
  if (assignments.length === 0) return undefined;
  return assignments[assignments.length - 1][1];
}

async function retrieveStreamtape(url: string, ctx: PluginContext): Promise<SourceInput> {
  const origin = originOf(url);
  const playerUrl = url.replace("/v/", "/e/");
  const page = await getLinkPage(playerUrl, ctx, { referer: origin + "/" });
  const assignment = lastAssignment(await page.text());
  if (!assignment) {
    throw new PluginSourceError("extraction_failed", `Streamtape: no video link on ${url}`);
  }

  const redirectingLink = resolveAgainst(playerUrl, assembleLink(assignment) + "&stream=1");
  const headers = { Referer: origin + "/" };
  const redirect = await ctx.http.get(redirectingLink, { headers, follow_redirects: false });
  const target = redirect.response().headers.get("location");
  return { url: target || redirectingLink, mime_type: "video/mp4", headers };
}

// Mirrors of Streamtape, from the ResolveURL project's list.
export const streamtape = defineHoster({
  name: "Streamtape",
  domains: [
    "streamtape.com", "strtape.cloud", "streamtape.net", "streamta.pe", "streamtape.site",
    "strcloud.link", "strcloud.club", "strtpe.link", "streamtape.cc", "scloud.online", "stape.fun",
    "streamadblockplus.com", "shavetape.cash", "streamtape.to", "streamta.site",
    "streamadblocker.xyz", "tapewithadblock.org", "adblocktape.wiki", "antiadtape.com",
    "streamtape.xyz", "tapeblocker.com", "streamnoads.com", "tapeadvertisement.com",
    "tapeadsenjoyer.com", "watchadsontape.com", "tpead.net", "advertape.net",
    "strtape.site", "strtapeadblock.me", "gettapeads.com",
    "streamtapeadblock.art", "streamtapeadblockuser.xyz", "advtpe.com",
  ],
  retrieve: retrieveStreamtape,
});
