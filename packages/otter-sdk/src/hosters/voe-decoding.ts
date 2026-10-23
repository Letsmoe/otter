// VOE hides its stream sources in obfuscated page data. These are the
// reversible steps it combines; each variant of the page uses another order.

import type { SourceInput } from "../model.ts";
import { base64, mimeType } from "../extract.ts";

/** Separator strings VOE sprinkles into the encoded data. */
export const DEFAULT_MARKERS = ["@$", "^^", "~@", "%?", "*~", "!!", "#&"];

export function rot13(text: string): string {
  return text.replace(/[A-Za-z]/g, (letter) => {
    const base = letter <= "Z" ? 65 : 97;
    return String.fromCharCode(((letter.charCodeAt(0) - base + 13) % 26) + base);
  });
}

function shiftBack(text: string, shift: number): string {
  return Array.from(text, (letter) => String.fromCharCode(letter.charCodeAt(0) - shift)).join("");
}

export function reverse(text: string): string {
  return Array.from(text).reverse().join("");
}

export function stripMarkers(text: string, markers: string[]): string {
  return markers.reduce((stripped, marker) => stripped.split(marker).join(""), text);
}

/** rot13, strip markers, base64, shift by -3, reverse, base64: the format of the current player pages. */
export function decodeMarkedPayload(payload: string, markers: string[]): string {
  const unmarked = stripMarkers(rot13(payload), markers);
  return base64(reverse(shiftBack(base64(unmarked), 3)));
}

function sourceFromMap(map: Record<string, unknown>): SourceInput | undefined {
  const candidates: [string, boolean][] = [
    ["direct_access_url", true],
    ["mp4", true],
    ["source", false],
    ["hls", false],
    ["file", false],
  ];
  for (const [key, isDirectFile] of candidates) {
    const url = map[key];
    if (typeof url !== "string" || url === "") continue;
    if (isDirectFile) return { url, mime_type: "video/mp4" };
    return { url, mime_type: mimeType(url, "application/x-mpegURL") };
  }
  return undefined;
}

function sourceFromText(text: string): SourceInput | undefined {
  const hls = text.match(/https?:\/\/[^\s"']+\.m3u8[^\s"']*/i);
  if (hls) return { url: hls[0], mime_type: "application/x-mpegURL" };
  const mp4 = text.match(/https?:\/\/[^\s"']+\.mp4[^\s"']*/i);
  if (mp4) return { url: mp4[0], mime_type: "video/mp4" };
  return undefined;
}

/** A decoded payload is either a JSON map of sources or free text holding a URL. */
export function sourceFromDecoded(decoded: string): SourceInput | undefined {
  try {
    return sourceFromMap(JSON.parse(decoded));
  } catch {
    return sourceFromText(decoded);
  }
}

function safely<Result>(decode: () => Result | undefined): Result | undefined {
  try {
    return decode();
  } catch {
    return undefined;
  }
}

/** Older pages: MKGMa="..." holds rot13, base64, shift, reverse, base64. */
export function fromMkgmaVariable(html: string): SourceInput | undefined {
  const found = html.match(/MKGMa="([^"]+)"/);
  if (!found) return undefined;
  return safely(() => {
    const unscored = rot13(found[1]).replace(/_/g, "");
    return sourceFromDecoded(base64(reverse(shiftBack(base64(unscored), 3))));
  });
}

/** Older pages: a168c='...' holds base64 of the reversed JSON. */
export function fromA168cVariable(html: string): SourceInput | undefined {
  const found = html.match(/a168c\s*=\s*'([^']+)'/);
  if (!found) return undefined;
  return safely(() => sourceFromDecoded(reverse(base64(found[1].replace(/\\/g, "")))));
}

/** Current pages: `<script type="application/json">["payload"]</script>`. */
export function fromJsonScript(html: string, markers: string[]): SourceInput | undefined {
  const scripts = html.matchAll(/<script[^>]+type=["']application\/json["'][^>]*>([\s\S]*?)<\/script>/gi);
  for (const script of scripts) {
    const source = safely(() => {
      const payload = JSON.parse(script[1].trim());
      if (!Array.isArray(payload) || typeof payload[0] !== "string") return undefined;
      return sourceFromDecoded(decodeMarkedPayload(payload[0], markers));
    });
    if (source) return source;
  }
  return undefined;
}

/** The markers a page's companion script defines: `['@$','^^',...]`. */
export function markersFromScript(script: string): string[] | undefined {
  const list = script.match(/\[(?:'\W{2}'[,\]]){1,9}/);
  if (!list) return undefined;
  return list[0].slice(2, -2).split("','");
}

const PLAIN_PATTERNS = [
  /"hls"\s*:\s*"(https?:[^"]+\.m3u8[^"]*)"/i,
  /'hls'\s*:\s*'(https?:[^']+\.m3u8[^']*)'/i,
  /"file"\s*:\s*"(https?:[^"]+\.m3u8[^"]*)"/i,
  /src\s*:\s*["'](https?:[^"']+\.m3u8[^"']*)["']/i,
  /"file"\s*:\s*"(https?:[^"]+\.mp4[^"]*)"/i,
  /'file'\s*:\s*'(https?:[^']+\.mp4[^']*)'/i,
  /mp4["']:\s*["']([^"']+)["']/i,
];

export function fromPlainPatterns(html: string): SourceInput | undefined {
  for (const pattern of PLAIN_PATTERNS) {
    const found = pattern.exec(html);
    if (found) return { url: found[1], mime_type: mimeType(found[1]) };
  }
  return undefined;
}
