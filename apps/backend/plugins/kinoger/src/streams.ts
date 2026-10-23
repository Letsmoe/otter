import type {
  MediaRef,
  PluginContext,
  StreamListParams,
  TitleFields,
} from "@neoworks-dev/otter-sdk";
import { searchKinoger, type KinogerSearchEntry } from "./search.ts";

export interface PlayerData {
  /** Season → episode → link, both 0-based. */
  seasons: string[][];
}

// Kinoger inlines each provider's links as a `<player>.show(id, [[...]])` call; the argument is a season/episode grid.
const PLAYER_CALL_PATTERN = /(\w+)\.show\(\d+,\s*(\[\[[\s\S]*?\]\])\s*(?:,[\s\d.]+)?\)/g;
const NOT_A_PLAYER = ["jQuery", "document"];

export async function listStreams(params: StreamListParams, ctx: PluginContext) {
  const pageUrl = await locatePage(params.media, ctx);
  if (!pageUrl) return ctx.list([]);

  const page = await ctx.http.get(pageUrl);
  const players = [...parsePlayerCalls(await page.text()).values()];
  const urls = linksFor(players, params.media);
  return ctx.list(urls.map((url) => ({ url, language: "de" })));
}

function titleFieldsOf(media: MediaRef): TitleFields {
  if (media.object === "episode") return media.series;
  return media;
}

function searchQueriesOf(media: MediaRef): string[] {
  const fields = titleFieldsOf(media);
  const queries = [fields.title, fields.original_title];
  return queries.filter((query): query is string => !!query && query.trim() !== "");
}

/** The kinoger page for the title: the series page holds every season, so episodes and movies share the lookup. */
async function locatePage(media: MediaRef, ctx: PluginContext): Promise<string | undefined> {
  for (const query of searchQueriesOf(media)) {
    const entries = await searchKinoger(query, ctx);
    const entry = pickEntry(entries, media, ctx);
    if (entry) return ctx.http.absolute(entry.href);
  }
  return undefined;
}

// Kinoger's categories are unreliable, so a series match of the other kind is still accepted.
function pickEntry(entries: KinogerSearchEntry[], media: MediaRef, ctx: PluginContext): KinogerSearchEntry | undefined {
  const wantsSeries = media.object === "episode";
  const sameKind = entries.filter((entry) => entry.isSeries === wantsSeries);
  return ctx.match.best(sameKind, media) || ctx.match.best(entries, media);
}

function linksFor(players: PlayerData[], media: MediaRef): string[] {
  if (media.object === "movie") return linksAt(players, 0, 0);
  return linksAt(players, toIndex(media.season_number), toIndex(media.episode_number));
}

/** The link each player offers at a grid position, without duplicates. */
function linksAt(players: PlayerData[], seasonIndex: number, episodeIndex: number): string[] {
  const urls = players.map((player) => player.seasons[seasonIndex]?.[episodeIndex]);
  const found = urls.filter((url): url is string => !!url);
  return [...new Set(found)];
}

// Kinoger's grids are 0-based; TMDB's season and episode numbers are 1-based.
function toIndex(oneBased: number): number {
  return Math.max(oneBased - 1, 0);
}

function playerDataOf(gridText: string): PlayerData | undefined {
  try {
    const json = gridText.replace(/'/g, '"').replace(/,\s*]/g, "]");
    const grid: string[][] = JSON.parse(json);
    return { seasons: grid.map((episodes) => episodes.map((url) => url.trim()).filter(Boolean)) };
  } catch {
    return undefined;
  }
}

export function parsePlayerCalls(html: string): Map<string, PlayerData> {
  const players = new Map<string, PlayerData>();
  for (const call of html.matchAll(PLAYER_CALL_PATTERN)) {
    const playerName = call[1];
    if (NOT_A_PLAYER.includes(playerName)) continue;
    const playerData = playerDataOf(call[2]);
    if (playerData) players.set(playerName, playerData);
  }
  return players;
}
