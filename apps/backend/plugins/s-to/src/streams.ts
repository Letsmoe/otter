import type { HTMLElement } from "node-html-parser";
import {
  HttpError,
  type EpisodeRef,
  type PluginContext,
  type StreamInput,
  type StreamListParams,
} from "@letsmoe/otter-sdk";
import { episodePath, isSitePage, languageOf, parseSeriesList, type SeriesCandidate } from "./site.ts";

export async function listStreams({ media }: StreamListParams, ctx: PluginContext) {
  if (media.object !== "episode") return ctx.list([]);

  const series = await findSeries(media, ctx);
  if (!series) return ctx.list([]);

  const episodeRoot = await fetchEpisodePage(episodePath(series.path, media.season_number, media.episode_number), ctx);
  if (!episodeRoot) return ctx.list([]);

  const streams = await Promise.all(episodeRoot.querySelectorAll("button.link-box[data-play-url]").map((button) => streamOf(button, ctx)));
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
 * The play link redirects to the hoster, whose URL is what the backend stores. Behind its
 * captcha it stays on the site: then the play link itself is stored and passed in the app on play
 * (see sources.ts).
 */
async function hosterUrlOrPlayLink(playUrl: string, ctx: PluginContext): Promise<string> {
  const finalUrl = await ctx.http.finalUrl(playUrl);
  if (isSitePage(finalUrl, ctx)) return playUrl;
  return finalUrl;
}

async function streamOf(button: HTMLElement, ctx: PluginContext): Promise<StreamInput | null> {
  const playPath = button.getAttribute("data-play-url") || "";
  const provider = button.getAttribute("data-provider-name") || "";
  if (!playPath || !provider) return null;

  const languageLabel = button.getAttribute("data-language-label") || "";
  const languageId = button.getAttribute("data-language-id") || "";
  const url = await hosterUrlOrPlayLink(ctx.http.absolute(playPath), ctx);
  return {
    url,
    label: `${provider} — ${languageLabel}`,
    language: languageOf(languageId, languageLabel),
  };
}
