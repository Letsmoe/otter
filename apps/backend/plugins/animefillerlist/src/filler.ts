import type { FillerEpisodeInput, FillerKind, FillerListParams, List, PluginContext } from "@letsmoe/otter-sdk";
import type { HTMLElement } from "node-html-parser";

const SHOWS_PATH = "/shows";

/** The site's list pages number episodes across the whole show, so no season number is given. */
export async function listFiller({ series }: FillerListParams, ctx: PluginContext): Promise<List<FillerEpisodeInput>> {
  const path = await showPathOf(series, ctx);
  if (!path) return ctx.list<FillerEpisodeInput>([]);
  const page = await ctx.http.get(ctx.http.absolute(path), { allow_error_status: true });
  if (page.status === 404) return ctx.list<FillerEpisodeInput>([]);
  return ctx.list(episodesOf(await page.html()));
}

interface ShowLink {
  title: string;
  path: string;
}

/** The show whose name is the series' title or original title; undefined when the site doesn't list it. */
async function showPathOf(series: FillerListParams["series"], ctx: PluginContext): Promise<string | undefined> {
  const response = await ctx.http.get(ctx.http.absolute(SHOWS_PATH));
  const root = await response.html();
  const links = root.querySelectorAll("div.Group li a").flatMap(showLinkOf);
  return ctx.match.best(links, series)?.path;
}

function showLinkOf(anchor: HTMLElement): ShowLink[] {
  const path = anchor.getAttribute("href") || "";
  const title = anchor.text.trim();
  if (!path.startsWith(`${SHOWS_PATH}/`) || !title) return [];
  return [{ title, path }];
}

/** Every row of the episode table: its number and what the Type column says. */
export function episodesOf(root: HTMLElement): FillerEpisodeInput[] {
  return root.querySelectorAll("table.EpisodeList tbody tr").flatMap(episodeOf);
}

function episodeOf(row: HTMLElement): FillerEpisodeInput[] {
  const number = Number.parseInt(row.querySelector("td.Number")?.text.trim() || "", 10);
  const kind = kindOf(row.querySelector("td.Type")?.text || "");
  if (!Number.isInteger(number) || number < 1 || !kind) return [];
  return [{ number, kind }];
}

/** "Manga Canon", "Anime Canon", "Mixed Canon/Filler" and "Filler". */
export function kindOf(label: string): FillerKind | undefined {
  const lowered = label.toLowerCase();
  if (lowered.includes("mixed")) return "mixed";
  if (lowered.includes("filler")) return "filler";
  if (lowered.includes("canon")) return "canon";
  return undefined;
}
