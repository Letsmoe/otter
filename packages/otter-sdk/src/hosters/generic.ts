import { PluginSourceError } from "../errors.ts";
import type { PluginContext } from "../definition.ts";
import type { ExtractedMedia } from "../extract.ts";
import type { SourceInput } from "../model.ts";
import { parse } from "node-html-parser";
import { getLinkPage } from "./dead-link.ts";
import { defineHoster } from "./registry.ts";

const MIME_PREFERENCE = ["application/x-mpegURL", "application/dash+xml", "video/mp4", "video/webm"];

function preferred(candidates: ExtractedMedia[]): ExtractedMedia | undefined {
  for (const mimeType of MIME_PREFERENCE) {
    const found = candidates.find((candidate) => candidate.mime_type === mimeType);
    if (found) return found;
  }
  return undefined;
}

function videoTagSource(html: string, ctx: PluginContext): ExtractedMedia | undefined {
  const root = parse(html);
  for (const element of root.querySelectorAll("video[src], source[src]")) {
    const source = element.getAttribute("src") || "";
    if (source.startsWith("http") || source.startsWith("//")) {
      const url = ctx.http.absolute(source);
      return { url, mime_type: ctx.extract.mimeType(url) };
    }
  }
  return undefined;
}

function mediaOnPage(html: string, ctx: PluginContext): ExtractedMedia | undefined {
  const fromVideoTag = videoTagSource(html, ctx);
  if (fromVideoTag) return fromVideoTag;
  return preferred(ctx.extract.mediaUrls(html));
}

async function mediaInFrames(html: string, pageUrl: string, ctx: PluginContext): Promise<ExtractedMedia | undefined> {
  for (const frameUrl of ctx.extract.iframes(html, pageUrl)) {
    try {
      const frame = await ctx.http.get(frameUrl, { referer: pageUrl });
      const found = mediaOnPage(await frame.text(), ctx);
      if (found) return found;
    } catch {
      ctx.log(`generic: skipping unreachable frame ${frameUrl}`);
    }
  }
  return undefined;
}

/** For hosters nobody wrote an extractor for: takes the first media URL the page or its iframes show. */
export const genericHoster = defineHoster({
  name: "Generic",
  domains: [],
  async retrieve(url, ctx): Promise<SourceInput> {
    const page = await getLinkPage(url, ctx);
    const html = await page.text();
    const found = mediaOnPage(html, ctx) || (await mediaInFrames(html, url, ctx));
    if (!found) {
      throw new PluginSourceError("extraction_failed", `no stream URL found at ${url}`);
    }
    return { url: found.url, mime_type: found.mime_type };
  },
});
