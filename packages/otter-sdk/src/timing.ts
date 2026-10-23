import type { ResolveResult } from "./types.ts";

/**
 * Resolves a link and adds how long that took. Whether the media really plays
 * and how fast is measured by the backend, which probes the actual segments.
 */
export async function resolveWithTiming(resolve: () => Promise<ResolveResult>): Promise<ResolveResult> {
  const startedAt = Date.now();
  const result = await resolve();
  return { ...result, load_time_ms: Date.now() - startedAt };
}
