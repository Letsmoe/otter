import { PluginSourceError, type HttpResponse, type PluginContext } from "@neoworks-dev/otter-sdk";
import { isRecord, parseJson, stringField } from "./json.ts";
import { appendQueryParameter } from "./url.ts";
import { embedPathOf, lookupUrlOf, type LookupTarget } from "./target.ts";

/** Where the stream API lives and which single-use token opens it. */
export interface PlayerSession {
  /** Origin of the embed page; the player and every Referer/Origin derive from it. */
  playerOrigin: string;
  apiUrl: string;
  apiToken: string;
}

/** The stream API's answer. `streamUrls` is an array, or an encrypted base64 string. */
export interface StreamEnvelope {
  streamUrls: string | string[];
  decryptor?: DecryptorSource;
}

export type DecryptorSource = { wasmUrl: string } | { wasmBase64: string };

/** Walks vs_src -> embed page -> player page -> stream API (steps 1 to 3). */
export async function openPlayerSession(target: LookupTarget, ctx: PluginContext): Promise<PlayerSession> {
  const embedSourceUrl = await fetchEmbedSourceUrl(target, ctx);
  const playerPageUrl = await fetchPlayerPageUrl(embedSourceUrl, ctx);
  const playerConfig = await fetchPlayerConfig(playerPageUrl, embedSourceUrl, target, ctx);
  return {
    playerOrigin: new URL(embedSourceUrl).origin,
    apiUrl: playerConfig.apiUrl,
    apiToken: playerConfig.apiToken,
  };
}

/** Asks the stream API with the session's token (step 4). */
export async function fetchStreamEnvelope(session: PlayerSession, ctx: PluginContext): Promise<StreamEnvelope> {
  const apiUrl = appendQueryParameter(session.apiUrl, "api_token", session.apiToken);
  const response = await ctx.http.get(apiUrl, {
    referer: `${session.playerOrigin}/`,
    headers: { Accept: "application/json" },
    allow_error_status: true,
  });
  if (response.status >= 400) {
    throw new PluginSourceError("source_offline", `VidSrc: stream API answered ${response.status}`, response.status);
  }
  return parseStreamEnvelope(await readJsonObject(response, "stream API"));
}

async function fetchEmbedSourceUrl(target: LookupTarget, ctx: PluginContext): Promise<string> {
  const response = await ctx.http.get(lookupUrlOf(target, ctx), {
    referer: ctx.http.absolute(embedPathOf(target)),
    headers: { Accept: "application/json" },
  });
  const body = await readJsonObject(response, "vs_src");
  return requiredField(body, "src", "vs_src");
}

async function fetchPlayerPageUrl(embedSourceUrl: string, ctx: PluginContext): Promise<string> {
  const page = await ctx.http.get(embedSourceUrl, { referer: ctx.http.absolute("/") });
  const config = windowConfigOf(await page.text(), "CFG", ctx, "embed page");
  return new URL(requiredField(config, "playerUrl", "embed page"), embedSourceUrl).href;
}

async function fetchPlayerConfig(
  playerPageUrl: string,
  embedSourceUrl: string,
  target: LookupTarget,
  ctx: PluginContext,
): Promise<{ apiUrl: string; apiToken: string }> {
  const page = await ctx.http.get(playerPageUrl, { referer: embedSourceUrl });
  const config = windowConfigOf(await page.text(), "CONFIG", ctx, "player page");
  return {
    apiUrl: streamApiUrlOf(config, target),
    apiToken: requiredField(config, "apiToken", "player page"),
  };
}

/** Movie pages name the stream API in "api"; series pages only hold the show's "streamBase", the player appends the episode. */
function streamApiUrlOf(config: Record<string, unknown>, target: LookupTarget): string {
  const api = stringField(config, "api");
  if (api !== undefined && api !== "") return api;
  const streamBase = requiredField(config, "streamBase", "player page");
  if (target.kind === "movie") return `${streamBase}&stream_urls`;
  return `${streamBase}&season=${target.season}&episode=${target.episode}&stream_urls`;
}

function windowConfigOf(
  html: string,
  variableName: string,
  ctx: PluginContext,
  description: string,
): Record<string, unknown> {
  const config = ctx.extract.scriptJson(html, variableName);
  if (!isRecord(config)) {
    throw new PluginSourceError("extraction_failed", `VidSrc: window.${variableName} not found on the ${description}`);
  }
  return config;
}

async function readJsonObject(response: HttpResponse, description: string): Promise<Record<string, unknown>> {
  const parsed = parseJson(await response.text());
  if (!isRecord(parsed)) {
    throw new PluginSourceError("extraction_failed", `VidSrc: the ${description} answer is not a JSON object`);
  }
  return parsed;
}

function requiredField(record: Record<string, unknown>, key: string, description: string): string {
  const value = stringField(record, key);
  if (value === undefined || value === "") {
    throw new PluginSourceError("extraction_failed", `VidSrc: "${key}" missing from the ${description}`);
  }
  return value;
}

function parseStreamEnvelope(body: Record<string, unknown>): StreamEnvelope {
  const data = body.data;
  if (!isRecord(data)) throw new PluginSourceError("extraction_failed", "VidSrc: the stream API has no data");
  const streamUrls = data.stream_urls;
  if (typeof streamUrls !== "string" && !Array.isArray(streamUrls)) {
    throw new PluginSourceError("extraction_failed", "VidSrc: stream_urls is neither a string nor a list");
  }
  return { streamUrls, decryptor: decryptorSourceOf(body.vs) };
}

function decryptorSourceOf(vs: unknown): DecryptorSource | undefined {
  if (!isRecord(vs)) return undefined;
  const wasmUrl = stringField(vs, "wasm_url");
  if (wasmUrl !== undefined) return { wasmUrl };
  const wasmBase64 = stringField(vs, "wasm");
  if (wasmBase64 !== undefined) return { wasmBase64 };
  return undefined;
}
