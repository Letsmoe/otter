import type { PluginContext } from "../definition.ts";
import { originOf } from "../url.ts";

const API_SCRIPT_URL = "https://www.google.com/recaptcha/api.js";
const ANCHOR_URL = "https://www.google.com/recaptcha/api2/anchor";
const RELOAD_URL = "https://www.google.com/recaptcha/api2/reload";

function siteKeyOf(html: string): string | undefined {
  const found = html.match(/(?:src="https:\/\/www\.google\.com\/recaptcha\/api\.js\?.*?render|data-sitekey)="?([^"&]+)/);
  if (!found) return undefined;
  return found[1];
}

function encodeOrigin(origin: string): string {
  return Buffer.from(`${origin}:443`).toString("base64").replace(/=+$/, "");
}

async function fetchReleaseVersion(siteKey: string, ctx: PluginContext, referer: string): Promise<string | undefined> {
  const script = await ctx.http.get(`${API_SCRIPT_URL}?render=${siteKey}`, { referer });
  const found = (await script.text()).match(/releases\/([^/]+)/);
  if (!found) return undefined;
  return found[1];
}

async function fetchAnchorToken(siteKey: string, origin: string, version: string, ctx: PluginContext, referer: string) {
  const query = new URLSearchParams({
    ar: "1",
    k: siteKey,
    co: encodeOrigin(origin),
    hl: "en",
    v: version,
    size: "invisible",
    cb: "123456789",
  });
  const anchor = await ctx.http.get(`${ANCHOR_URL}?${query.toString()}`, { referer });
  const found = (await anchor.text()).match(/recaptcha-token.+?="([^"]+)/);
  if (!found) return undefined;
  return found[1];
}

/**
 * Obtains an invisible reCAPTCHA v3 token for a page, the way the page's own
 * script does. Returns an empty string when the page has no reCAPTCHA or Google refuses.
 */
export async function recaptchaToken(html: string, pageUrl: string, ctx: PluginContext): Promise<string> {
  const siteKey = siteKeyOf(html);
  if (!siteKey) return "";
  const origin = originOf(pageUrl);
  const version = await fetchReleaseVersion(siteKey, ctx, pageUrl);
  if (!version) return "";
  const anchorToken = await fetchAnchorToken(siteKey, origin, version, ctx, pageUrl);
  if (!anchorToken) return "";

  const reload = await ctx.http.post(`${RELOAD_URL}?k=${siteKey}`, {
    body: { v: version, reason: "q", k: siteKey, c: anchorToken, sa: "", co: encodeOrigin(origin) },
    referer: ANCHOR_URL,
  });
  const found = (await reload.text()).match(/rresp","([^"]+)/);
  if (!found) return "";
  return found[1];
}
