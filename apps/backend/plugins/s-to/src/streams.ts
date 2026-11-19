import type { HTMLElement } from "node-html-parser";
import {
  HttpError,
  type EpisodeRef,
  type PluginContext,
  type StreamInput,
  type StreamListParams,
} from "@letsmoe/otter-sdk";
import { episodePath, languageOf, linkUrl, parseSeriesList, type SeriesCandidate } from "./site.ts";

export async function listStreams({ media }: StreamListParams, ctx: PluginContext) {
  if (media.object !== "episode") return ctx.list([]);

  const series = await findSeries(media, ctx);
  if (!series) return ctx.list([]);

  const pagePath = episodePath(series.path, media.season_number, media.episode_number);
  const episodeRoot = await fetchEpisodePage(pagePath, ctx);
  if (!episodeRoot) return ctx.list([]);

  const pageUrl = ctx.http.absolute(pagePath);
  const streams = episodeRoot.querySelectorAll("button.link-box[data-play-url]").map((button) => streamOf(button, pageUrl));
  return ctx.list(streams.filter((stream): stream is StreamInput => stream !== null));
}

async function findSeries(media: EpisodeRef, ctx: PluginContext): Promise<SeriesCandidate | undefined> {
  const seriesList = await (await ctx.http.get("/serien")).html();
  return ctx.match.best(parseSeriesList(seriesList), media);
}

/** An episode the site does not have is not an error. */
async function fetchEpisodePage(path: string, ctx: PluginContext): Promise<HTMLElement | undefined> {
  try {
    return await (await ctx.http.get(path)).html();
  } catch (error) {
    if (error instanceof HttpError && error.status === 404) return undefined;
    throw error;
  }
}

/**
 * A play link ("/r?t=…") only works inside the episode page's player, behind its captcha, and
 * its token changes with every page load. So the stored link is the episode page plus the
 * button's provider and language; sources.ts opens the page in the app on play and clicks it.
 */
function streamOf(button: HTMLElement, pageUrl: string): StreamInput | null {
  const provider = button.getAttribute("data-provider-name") || "";
  // A button without a play link leads nowhere, even on the site.
  if (!provider || !button.getAttribute("data-play-url")) return null;

  const languageLabel = button.getAttribute("data-language-label") || "";
  const languageId = button.getAttribute("data-language-id") || "";
  return {
    url: linkUrl(pageUrl, { provider, languageId }),
    label: `${provider} — ${languageLabel}`,
    language: languageOf(languageId, languageLabel),
  };
}
