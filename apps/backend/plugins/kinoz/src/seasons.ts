import type { HTMLElement } from "node-html-parser";

export interface SeasonSelection {
  /** "?Addr=<slug>&SeriesID=<id>", the prefix of the per-episode mirror request. */
  seriesQuery: string;
  episodesBySeason: Map<number, number[]>;
}

/**
 * Reads `select#SeasonSelection`: each option is a season number whose `rel`
 * lists its episode numbers ("1,2,3").
 */
export function seasonSelectionOf(root: HTMLElement): SeasonSelection | undefined {
  const select = root.querySelector("select#SeasonSelection");
  if (!select) return undefined;
  const seriesQuery = select.getAttribute("rel") || "";
  if (!seriesQuery.startsWith("?")) return undefined;

  const episodesBySeason = new Map<number, number[]>();
  for (const option of select.querySelectorAll("option")) {
    const seasonNumber = Number(option.getAttribute("value"));
    if (!Number.isInteger(seasonNumber)) continue;
    episodesBySeason.set(seasonNumber, episodeNumbersOf(option.getAttribute("rel") || ""));
  }
  return { seriesQuery, episodesBySeason };
}

function episodeNumbersOf(list: string): number[] {
  return list
    .split(",")
    .map((part) => Number(part.trim()))
    .filter((episodeNumber) => Number.isInteger(episodeNumber) && episodeNumber > 0);
}

export function episodeListed(seasons: SeasonSelection, seasonNumber: number, episodeNumber: number): boolean {
  const episodes = seasons.episodesBySeason.get(seasonNumber);
  if (!episodes) return false;
  return episodes.includes(episodeNumber);
}
