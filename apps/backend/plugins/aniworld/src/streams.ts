import type { HTMLElement } from "node-html-parser";
import {
  HttpError,
  type EpisodeRef,
  type PluginContext,
  type StreamInput,
  type StreamListParams,
} from "@neoworks-dev/otter-sdk";
import { episodePath, hosterNameOf, languageLabelOf, languageOf, parseSeriesList, type SeriesCandidate } from "./site.ts";

export async function listStreams({ media }: StreamListParams, ctx: PluginContext) {
  if (media.object !== "episode") return ctx.list([]);

  const series = await findSeries(media, ctx);
  if (!series) return ctx.list([]);

  const episodeRoot = await fetchEpisodePage(episodePath(series.path, media.season_number, media.episode_number), ctx);
  if (!episodeRoot) return ctx.list([]);

  const streams = episodeRoot.querySelectorAll("li[data-link-target]").map((linkItem) => streamOf(linkItem, ctx));
  return ctx.list(streams.filter((stream): stream is StreamInput => stream !== null));
}

async function findSeries(media: EpisodeRef, ctx: PluginContext): Promise<SeriesCandidate | undefined> {
  const animeList = await (await ctx.http.get("/animes")).html();
  return ctx.match.best(parseSeriesList(animeList), media);
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

/** The link stays the site's /redirect/<id> URL; the backend follows it at play time. */
function streamOf(linkItem: HTMLElement, ctx: PluginContext): StreamInput | null {
  const playPath = linkItem.getAttribute("data-link-target") || "";
  const hoster = hosterNameOf(linkItem);
  if (!playPath || !hoster) return null;

  const languageKey = linkItem.getAttribute("data-lang-key") || "";
  return {
    url: ctx.http.absolute(playPath),
    label: labelOf(hoster, languageKey),
    language: languageOf(languageKey),
  };
}

function labelOf(hoster: string, languageKey: string): string {
  const languageLabel = languageLabelOf(languageKey);
  if (languageLabel === "") return hoster;
  return `${hoster} — ${languageLabel}`;
}
