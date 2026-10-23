import { PluginSourceError, type PluginContext } from "@neoworks-dev/otter-sdk";
import { TOKEN_PLACEHOLDER } from "./constants.ts";
import { isRecord, parseJson, stringField } from "./json.ts";
import { appendQueryParameter } from "./url.ts";

/**
 * Turns the first server that hands out a token into its playable master playlist.
 * Servers rate-limit, so any failure just moves on to the next one.
 */
export async function tokenizedServerUrl(
  streamUrls: string[],
  playerOrigin: string,
  ctx: PluginContext,
): Promise<string> {
  for (const streamUrl of streamUrls) {
    const tokenized = await tryTokenize(streamUrl, playerOrigin, ctx);
    if (tokenized !== undefined) return tokenized;
  }
  throw new PluginSourceError("source_offline", "VidSrc: no server handed out a playable token");
}

async function tryTokenize(streamUrl: string, playerOrigin: string, ctx: PluginContext): Promise<string | undefined> {
  try {
    return await tokenize(streamUrl, playerOrigin, ctx);
  } catch (error) {
    ctx.log(`server ${new URL(streamUrl).origin} failed`, String(error));
    return undefined;
  }
}

async function tokenize(streamUrl: string, playerOrigin: string, ctx: PluginContext): Promise<string> {
  const generateUrl = new URL("/generate.php", streamUrl).href;
  const response = await ctx.http.get(generateUrl, {
    referer: `${playerOrigin}/`,
    headers: { Origin: playerOrigin },
    retries: 0,
  });
  const token = parseToken(await response.text());
  if (!isPlausibleToken(token)) throw new Error("the server answered without a token");
  return applyToken(streamUrl, token);
}

/** A plain token, or a JSON string/object carrying one, like the site's player reads it. */
export function parseToken(text: string): string {
  const trimmed = text.trim();
  if (!trimmed.startsWith("{") && !trimmed.startsWith("[")) return trimmed;
  return tokenFromJson(trimmed);
}

function tokenFromJson(text: string): string {
  const parsed = parseJson(text);
  if (parsed === undefined) return text;
  if (typeof parsed === "string") return parsed;
  if (!isRecord(parsed)) return "";
  return firstStringField(parsed, ["token", "data", "string", "result"]);
}

function firstStringField(record: Record<string, unknown>, keys: string[]): string {
  for (const key of keys) {
    const value = stringField(record, key);
    if (value !== undefined && value !== "") return value;
  }
  return "";
}

function isPlausibleToken(token: string): boolean {
  return token.length > 0 && !/[\s<>]/.test(token);
}

export function applyToken(streamUrl: string, token: string): string {
  if (streamUrl.includes(TOKEN_PLACEHOLDER)) return streamUrl.split(TOKEN_PLACEHOLDER).join(token);
  return appendQueryParameter(streamUrl, "token", token);
}
