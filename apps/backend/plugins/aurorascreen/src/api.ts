import type { PluginContext } from "@neoworks-dev/otter-sdk";

export const STREAM_HOST = "stream.aurorascreen.org";
const STREAM_API_BASE = `https://${STREAM_HOST}`;

/** The API answers only to requests that come from the site. */
export const REFERER = "https://aurorascreen.org/";

/** The API servers that still answer, by name; the label is shown for their streams. */
export const SERVER_LABELS: Record<string, string> = {
  jupiter: "AuroraScreen Jupiter",
  venus: "AuroraScreen Venus",
};

export interface LookupTarget {
  title: string;
  year?: number;
  tmdbId: number;
  season?: number;
  episode?: number;
}

export interface StreamEntry {
  source?: string;
  /** "2160p" | "1080p" | ... | "HLS" */
  quality?: string;
  type?: string;
  url?: string;
}

interface LookupBody {
  success?: boolean;
  streams?: StreamEntry[];
}

/**
 * The API lookup of one server. It is kept as the stream's link: the playable
 * URLs expire, so the lookup is asked again at play time.
 */
export function lookupUrl(server: string, target: LookupTarget): string {
  const parameters = [`id=${target.tmdbId}`, `name=${encodeURIComponent(target.title)}`];
  if (target.year !== undefined) parameters.push(`year=${target.year}`);
  if (target.season !== undefined && target.episode !== undefined) {
    parameters.push(`s=${target.season}`, `e=${target.episode}`);
  }
  return `${STREAM_API_BASE}/${server}?${parameters.join("&")}`;
}

/** A server with nothing for the title answers 502 with success:false, which is an empty list. */
export async function fetchStreamEntries(url: string, ctx: PluginContext): Promise<StreamEntry[]> {
  const response = await ctx.http.get(url, { referer: REFERER, allow_error_status: true, retries: 0 });
  const body = parseLookupBody(await response.text());
  if (!body || body.success !== true) return [];
  return body.streams || [];
}

function parseLookupBody(text: string): LookupBody | undefined {
  try {
    return JSON.parse(text) as LookupBody;
  } catch {
    return undefined;
  }
}
