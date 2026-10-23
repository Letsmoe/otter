import { PluginSourceError } from "../errors.ts";
import type { PluginContext } from "../definition.ts";
import type { SourceInput } from "../model.ts";
import {
  DEFAULT_MARKERS,
  fromA168cVariable,
  fromJsonScript,
  fromMkgmaVariable,
  fromPlainPatterns,
  markersFromScript,
} from "./voe-decoding.ts";
import { originOf, resolveAgainst } from "../url.ts";
import { getLinkPage } from "./dead-link.ts";
import { defineHoster } from "./registry.ts";
import { VOE_DOMAINS } from "./voe-domains.ts";

const MAX_REDIRECT_PAGES = 3;
const REDIRECT_PAGE_MAX_LENGTH = 3000;

interface PlayerPage {
  url: string;
  html: string;
}

/**
 * Small pages that only forward the browser: either to a mirror (the else
 * branch of the permanentToken check) or via `window.location.href`.
 */
function redirectTarget(html: string): string | undefined {
  if (html.length > REDIRECT_PAGE_MAX_LENGTH) return undefined;
  if (!/permanentToken|const currentUrl/.test(html)) return undefined;
  const elseBranch = html.match(/}\s*else\s*\{[^}]*window\.location\.href\s*=\s*['"]([^'"]+)['"]/);
  if (elseBranch) return elseBranch[1];
  const direct = html.match(/window\.location\.href\s*=\s*['"]([^'"]+)['"]/);
  if (direct) return direct[1];
  return undefined;
}

async function loadPlayerPage(url: string, ctx: PluginContext): Promise<PlayerPage> {
  let current = url;
  for (let hop = 0; hop < MAX_REDIRECT_PAGES; hop++) {
    const response = await getLinkPage(current, ctx);
    const html = await response.text();
    const target = redirectTarget(html);
    if (!target) return { url: current, html };
    current = resolveAgainst(current, target);
  }
  throw new PluginSourceError("extraction_failed", `VOE: too many redirect pages from ${url}`);
}

/** Current pages keep their marker list in a script next to the payload. */
async function markersOf(html: string, pageUrl: string, ctx: PluginContext): Promise<string[]> {
  const scriptSource = html.match(/json">\["[^"]+"\]<\/script>\s*<script\s*src="([^"]+)"/);
  if (!scriptSource) return DEFAULT_MARKERS;
  try {
    const script = await ctx.http.get(resolveAgainst(pageUrl, scriptSource[1]), { referer: pageUrl });
    return markersFromScript(await script.text()) || DEFAULT_MARKERS;
  } catch {
    return DEFAULT_MARKERS;
  }
}

async function extractSource(page: PlayerPage, ctx: PluginContext): Promise<SourceInput | undefined> {
  const markers = await markersOf(page.html, page.url, ctx);
  return (
    fromJsonScript(page.html, markers) ||
    fromMkgmaVariable(page.html) ||
    fromA168cVariable(page.html) ||
    fromPlainPatterns(page.html)
  );
}

async function retrieveVoe(url: string, ctx: PluginContext): Promise<SourceInput> {
  const page = await loadPlayerPage(url, ctx);
  const source = await extractSource(page, ctx);
  if (!source) {
    throw new PluginSourceError("extraction_failed", `VOE: no stream found on ${page.url}`);
  }
  return { ...source, headers: { Referer: originOf(page.url) + "/" } };
}

export const voe = defineHoster({ name: "VOE", domains: VOE_DOMAINS, retrieve: retrieveVoe });
