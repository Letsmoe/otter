import { parse } from "node-html-parser";
import type { MimeType } from "./model.ts";
import { unpack } from "./packer.ts";
import { resolveAgainst } from "./url.ts";

export interface ExtractedMedia {
  url: string;
  mime_type: MimeType;
}

const URL_TOKEN = /https?:\/\/[^\s"'<>\\)]+/gi;
const MEDIA_PATH = /\.(m3u8|mpd|mp4|webm)(?:[?#]|$)/i;

/** The media type a URL's extension says; `fallback` applies when it has no known one. */
export function mimeType(url: string, fallback: MimeType = "video/mp4"): MimeType {
  if (/\.m3u8/i.test(url)) return "application/x-mpegURL";
  if (/\.mpd/i.test(url)) return "application/dash+xml";
  if (/\.webm/i.test(url)) return "video/webm";
  if (/\.mp4/i.test(url)) return "video/mp4";
  return fallback;
}

/** Makes URLs written inside JSON or HTML attributes plain: `https:\/\/a&b` -> `https://a&b`. */
function unescapeUrls(text: string): string {
  return text
    .replace(/\\\//g, "/")
    .replace(/\\u0026/gi, "&")
    .replace(/&amp;/g, "&");
}

function mediaUrlsIn(text: string): string[] {
  const tokens = unescapeUrls(text).match(URL_TOKEN) || [];
  const urls: string[] = [];
  for (const token of tokens) {
    const url = token.replace(/[,;]+$/, "");
    if (MEDIA_PATH.test(url)) urls.push(url);
  }
  return urls;
}

/** Every m3u8/mpd/mp4/webm URL in a page, including those inside packed scripts, in page order. */
export function mediaUrls(html: string): ExtractedMedia[] {
  const unpacked = unpack(html);
  const found = mediaUrlsIn(html);
  if (unpacked !== undefined) found.push(...mediaUrlsIn(unpacked));
  const unique = [...new Set(found)];
  return unique.map((url) => ({ url, mime_type: mimeType(url) }));
}

function usableFrameUrl(source: string, pageUrl: string): string | undefined {
  if (!source || /^(javascript|about|data):/i.test(source)) return undefined;
  try {
    return resolveAgainst(pageUrl, source);
  } catch {
    return undefined;
  }
}

/** Absolute URLs of the page's iframes. */
export function iframes(html: string, pageUrl: string): string[] {
  const urls: string[] = [];
  for (const frame of parse(html).querySelectorAll("iframe")) {
    const source = frame.getAttribute("src") || frame.getAttribute("data-src") || "";
    const url = usableFrameUrl(source, pageUrl);
    if (url) urls.push(url);
  }
  return urls;
}

function escapeRegExp(text: string): string {
  return text.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

/** The balanced JSON object or array starting at `start`, or undefined if it never closes. */
function balancedJson(text: string, start: number): string | undefined {
  const opening = text[start];
  const closing = opening === "{" ? "}" : "]";
  let depth = 0;
  let insideString = false;
  for (let position = start; position < text.length; position++) {
    const character = text[position];
    if (insideString) {
      if (character === "\\") position += 1;
      else if (character === '"') insideString = false;
      continue;
    }
    if (character === '"') insideString = true;
    else if (character === opening) depth += 1;
    else if (character === closing) depth -= 1;
    if (depth === 0) return text.slice(start, position + 1);
  }
  return undefined;
}

function tryParse(json: string | undefined): unknown {
  if (json === undefined) return undefined;
  try {
    return JSON.parse(json);
  } catch {
    return undefined;
  }
}

function jsonScriptWithId(html: string, id: string): unknown {
  const script = parse(html).querySelector(`script[id="${id}"]`);
  if (!script) return undefined;
  return tryParse(script.textContent);
}

/**
 * The JSON value assigned to `variableName` in a page script
 * (`var data = {...}`, `window.data = [...]`, `"data": {...}`), or the content
 * of `<script id="variableName">`. Undefined when absent or not valid JSON.
 */
export function scriptJson(html: string, variableName: string): unknown {
  const fromScriptTag = jsonScriptWithId(html, variableName);
  if (fromScriptTag !== undefined) return fromScriptTag;

  const assignment = new RegExp(`${escapeRegExp(variableName)}["']?\\s*[:=]\\s*([\\[{])`);
  const found = assignment.exec(html);
  if (!found) return undefined;
  const start = found.index + found[0].length - 1;
  return tryParse(balancedJson(html, start));
}

/** Decodes base64, also the URL-safe alphabet and missing padding. */
export function base64(text: string): string {
  const standard = text.replace(/\s+/g, "").replace(/-/g, "+").replace(/_/g, "/");
  const padding = (4 - (standard.length % 4)) % 4;
  return Buffer.from(standard + "=".repeat(padding), "base64").toString("utf-8");
}

export const extract = { mediaUrls, unpack, iframes, scriptJson, base64, mimeType };
export type Extractor = typeof extract;
