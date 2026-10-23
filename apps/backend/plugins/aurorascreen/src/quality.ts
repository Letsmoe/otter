import type { StreamEntry } from "./api.ts";

export function isHlsEntry(entry: StreamEntry): boolean {
  if (!entry.url) return false;
  return entry.type === "hls" || entry.quality === "HLS";
}

/** The pixel height of a "1080p"-style quality; 0 when the server only says "HLS". */
function heightOf(quality: string | undefined): number {
  const match = /^(\d+)p$/.exec(quality || "");
  if (!match) return 0;
  return Number(match[1]);
}

/** The highest HLS entry; the first one wins when the server names no heights. */
export function bestHlsEntry(entries: StreamEntry[]): StreamEntry | undefined {
  let best: StreamEntry | undefined;
  for (const entry of entries.filter(isHlsEntry)) {
    if (!best || heightOf(entry.quality) > heightOf(best.quality)) {
      best = entry;
    }
  }
  return best;
}

/** The quality to advertise: the entry's own "1080p"-style label, or nothing. */
export function advertisedQuality(entry: StreamEntry): string | undefined {
  if (heightOf(entry.quality) === 0) return undefined;
  return entry.quality;
}
