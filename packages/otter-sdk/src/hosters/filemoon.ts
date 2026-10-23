import { PluginDeadLinkError, PluginSourceError } from "../errors.ts";
import type { HttpResponse } from "../http-client.ts";
import type { PluginContext } from "../definition.ts";
import type { SourceInput } from "../model.ts";
import { originOf } from "../url.ts";
import { decryptAesGcm } from "../aes-gcm.ts";
import { apiReportsMissingFile, getLinkPage } from "./dead-link.ts";
import { defineHoster } from "./registry.ts";

const MAX_FRAME_DEPTH = 2;

interface PlayerPage {
  url: string;
  html: string;
}

function sourceFor(streamUrl: string, page: PlayerPage, ctx: PluginContext): SourceInput {
  return {
    url: streamUrl,
    mime_type: ctx.extract.mimeType(streamUrl, "application/x-mpegURL"),
    headers: { Referer: originOf(page.url) + "/" },
  };
}

/** The embed page names the playlist in a data attribute, or packs it into its player script. */
function streamUrlOf(html: string, ctx: PluginContext): string | undefined {
  const attribute = html.match(/data-hls-url="([^"]+)/);
  if (attribute) return attribute[1].replace(/&amp;/g, "&");
  const media = ctx.extract.mediaUrls(html);
  const playlist = media.find((candidate) => candidate.mime_type === "application/x-mpegURL");
  if (playlist) return playlist.url;
  if (media.length > 0) return media[0].url;
  return undefined;
}

/** Only the embed page of the link itself says whether the file exists; frames inside it are secondary. */
async function fetchPage(pageUrl: string, referer: string, depth: number, ctx: PluginContext): Promise<HttpResponse> {
  if (depth === 0) return getLinkPage(pageUrl, ctx, { referer });
  return ctx.http.get(pageUrl, { referer });
}

async function findStream(pageUrl: string, referer: string, depth: number, ctx: PluginContext): Promise<SourceInput | undefined> {
  const response = await fetchPage(pageUrl, referer, depth, ctx);
  const page = { url: response.url || pageUrl, html: await response.text() };
  const streamUrl = streamUrlOf(page.html, ctx);
  if (streamUrl) return sourceFor(streamUrl, page, ctx);
  if (depth >= MAX_FRAME_DEPTH) return undefined;

  // Newer embeds only hold an iframe to the actual player on another domain.
  const frames = ctx.extract.iframes(page.html, page.url);
  if (frames.length === 0) return undefined;
  return findStream(frames[0], page.url, depth + 1, ctx);
}

interface EncryptedPlayback {
  iv: string;
  payload: string;
  key_parts: string[];
  version: string;
}

interface VideoSource {
  url: string;
  height?: number;
}

const KEY_PART_VERSIONS = 20;

function bytesOfBase64Url(text: string): Uint8Array {
  const standard = text.replace(/-/g, "+").replace(/_/g, "/");
  const padding = (4 - (standard.length % 4)) % 4;
  return Uint8Array.from(Buffer.from(standard + "=".repeat(padding), "base64"));
}

/** The playback key is two of the 1-based key parts, picked by the playback version n (1..20): parts n and 31 - n. */
function keyOf(playback: EncryptedPlayback): Uint8Array {
  const version = parseInt(playback.version, 10);
  if (!(version >= 1 && version <= KEY_PART_VERSIONS)) {
    throw new PluginSourceError("extraction_failed", `Filemoon: unknown playback version ${playback.version}`);
  }
  const firstPart = bytesOfBase64Url(playback.key_parts[version - 1]);
  const secondPart = bytesOfBase64Url(playback.key_parts[31 - version - 1]);
  const key = new Uint8Array(firstPart.length + secondPart.length);
  key.set(firstPart);
  key.set(secondPart, firstPart.length);
  return key;
}

function decryptSources(playback: EncryptedPlayback): VideoSource[] {
  const plaintext = decryptAesGcm(keyOf(playback), bytesOfBase64Url(playback.iv), bytesOfBase64Url(playback.payload));
  const decoded = JSON.parse(Buffer.from(plaintext).toString("utf8"));
  return decoded.sources || [];
}

function bestSource(sources: VideoSource[]): VideoSource | undefined {
  let best: VideoSource | undefined;
  for (const source of sources) {
    if (!best || (source.height || 0) > (best.height || 0)) best = source;
  }
  return best;
}

function videoCodeOf(url: string): string {
  const segments = new URL(url).pathname.split("/").filter(Boolean);
  return segments[segments.length - 1] || "";
}

/** Domains without the video API answer with the SPA's HTML. */
function parseVideo(body: string): { playback?: EncryptedPlayback } {
  try {
    return JSON.parse(body);
  } catch {
    return {};
  }
}

/** The current embed is a single-page app; its video API serves the sources AES-GCM encrypted. */
async function findStreamViaApi(url: string, ctx: PluginContext): Promise<SourceInput | undefined> {
  const code = videoCodeOf(url);
  if (code === "") return undefined;
  const origin = originOf(url);
  const response = await ctx.http.get(`${origin}/api/videos/${code}`, {
    referer: `${origin}/e/${code}`,
    allow_error_status: true,
  });
  const body = await response.text();
  if (apiReportsMissingFile(response.status, body)) {
    throw new PluginDeadLinkError(`Filemoon: ${url} reports the video as missing`, response.status);
  }
  if (response.status >= 400) return undefined;
  const video = parseVideo(body);
  if (!video.playback) return undefined;
  const best = bestSource(decryptSources(video.playback));
  if (!best) return undefined;
  return sourceFor(best.url, { url: origin + "/", html: "" }, ctx);
}

async function retrieveFilemoon(url: string, ctx: PluginContext): Promise<SourceInput> {
  const apiSource = await findStreamViaApi(url, ctx);
  if (apiSource) return apiSource;
  const source = await findStream(url, originOf(url) + "/", 0, ctx);
  if (!source) {
    throw new PluginSourceError("extraction_failed", `Filemoon: no stream found on ${url}`);
  }
  return source;
}

export const filemoon = defineHoster({
  name: "Filemoon",
  domains: ["filemoon.sx", "filemoon.to", "filemoon.in", "filemoon.link", "filemoon.nl", "filemoon.org"],
  retrieve: retrieveFilemoon,
});
