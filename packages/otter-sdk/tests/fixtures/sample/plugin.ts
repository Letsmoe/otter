import { definePlugin } from "../../../src/plugin.ts";
import type { PluginContext } from "../../../src/plugin.ts";
import type { MediaRef, SourceInput, SourceRetrieveParams, StreamListParams } from "../../../src/model.ts";
import { PluginDeadLinkError, PluginSourceError } from "../../../src/errors.ts";

interface SearchResult {
  title: string;
  year: number;
  href: string;
}

async function search(media: MediaRef, ctx: PluginContext): Promise<SearchResult[]> {
  const title = media.object === "movie" ? media.title : media.series.title;
  const page = await ctx.http.get(`/search?q=${encodeURIComponent(title)}`);
  const root = await page.html();
  return root.querySelectorAll("a.result").map((link) => ({
    title: link.text.replace(/\s*\(\d{4}\)\s*$/, ""),
    year: Number(link.getAttribute("data-year")),
    href: link.getAttribute("href") || "",
  }));
}

async function playerLinks(detailUrl: string, ctx: PluginContext) {
  const detail = await ctx.http.get(detailUrl);
  const detailHtml = await detail.text();
  const streams = [];
  for (const frameUrl of ctx.extract.iframes(detailHtml, detailUrl)) {
    const player = await ctx.http.get(frameUrl, { referer: detailUrl });
    for (const media of ctx.extract.mediaUrls(await player.text())) {
      streams.push({ url: media.url, language: "de", quality: "1080p" });
    }
  }
  return streams;
}

async function listStreams(params: StreamListParams, ctx: PluginContext) {
  const candidates = await search(params.media, ctx);
  const found = ctx.match.best(candidates, params.media, { year: (candidate) => candidate.year });
  if (!found) return ctx.list([]);
  ctx.log("matched", { title: found.title });
  const detailUrl = ctx.http.absolute(found.href);
  return ctx.list(await playerLinks(detailUrl, ctx));
}

async function retrieveSource(params: SourceRetrieveParams, ctx: PluginContext): Promise<SourceInput> {
  if (params.url.includes("/gone")) {
    throw new PluginSourceError("extraction_failed", "video was removed");
  }
  if (params.url.includes("/deleted")) {
    throw new PluginDeadLinkError("video was deleted", 404);
  }
  return ctx.hosters.retrieve(params.url, ctx);
}

export default definePlugin({
  id: "sample",
  name: "Sample",
  version: "0.0.1",
  base_url: "http://sample.invalid",
  languages: ["de"],
  media: ["movie", "episode"],
  streams: { list: listStreams },
  sources: { domains: [], fallback: true, retrieve: retrieveSource },
});
