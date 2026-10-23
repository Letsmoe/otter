import type { StreamInput } from "@neoworks-dev/otter-sdk";

/** A hoster group as the site lists it: a server name (with its language) and the links it offers. */
export interface HosterGroup {
  name: string;
  mirrors: string[];
}

interface ServerEntry {
  name: string;
  mirrors?: string[];
}

export interface EpisodeGroupEntry {
  host: string;
  urls?: string[];
}

// The movie page embeds its server list as a JavaScript array literal.
const SERVERS_PATTERN = /const SERVERS = (\[[\s\S]*?\]);/;

// Embed frontends that wrap other hosters; their links are not playable on their own.
const EMBED_FRONTENDS = ["meinecloud.click", "vidsync.xyz", "cinesrc.st"];

const LANGUAGE_BY_SUFFIX: Record<string, string> = { DE: "de", EN: "en", FR: "fr" };
const LANGUAGE_SUFFIX_PATTERN = /\((DE|EN|FR)\)\s*$/i;

export function parseMovieGroups(html: string): HosterGroup[] {
  const match = SERVERS_PATTERN.exec(html);
  if (!match) return [];
  const entries = JSON.parse(match[1]) as ServerEntry[];
  return entries.map((entry) => ({ name: entry.name, mirrors: entry.mirrors || [] }));
}

export function parseEpisodeGroups(entries: EpisodeGroupEntry[]): HosterGroup[] {
  return entries.map((entry) => ({ name: entry.host, mirrors: entry.urls || [] }));
}

/** "Server A1 (DE)" -> "de"; groups without a language suffix have none. */
export function languageOfGroup(groupName: string): string | undefined {
  const match = LANGUAGE_SUFFIX_PATTERN.exec(groupName);
  if (!match) return undefined;
  return LANGUAGE_BY_SUFFIX[match[1].toUpperCase()];
}

export function streamsOf(groups: HosterGroup[]): StreamInput[] {
  const streams: StreamInput[] = [];
  for (const group of groups) {
    streams.push(...streamsOfGroup(group));
  }
  return withoutDuplicates(streams);
}

function streamsOfGroup(group: HosterGroup): StreamInput[] {
  const language = languageOfGroup(group.name);
  return group.mirrors.filter((url) => !isEmbedFrontend(url)).map((url) => ({ url, language }));
}

function isEmbedFrontend(url: string): boolean {
  const host = new URL(url).hostname;
  return EMBED_FRONTENDS.some((domain) => host === domain || host.endsWith("." + domain));
}

/** The same link can appear in several groups; one per language is kept. */
function withoutDuplicates(streams: StreamInput[]): StreamInput[] {
  const seenKeys: string[] = [];
  return streams.filter((stream) => {
    const key = `${stream.language}|${stream.url}`;
    if (seenKeys.indexOf(key) !== -1) return false;
    seenKeys.push(key);
    return true;
  });
}
