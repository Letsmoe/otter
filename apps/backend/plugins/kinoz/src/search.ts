import type { HTMLElement } from "node-html-parser";
import { domainOf, PluginSourceError, type PluginContext } from "@letsmoe/otter-sdk";
import { slugFromHref } from "./paths.ts";

export interface SearchCandidate {
  title: string;
  slug: string;
  /** "/Stream/<slug>.html" */
  sourcePath: string;
}

/** Catalog rows of kinoz's site search; kinoz has no API. */
export async function searchCandidates(query: string, ctx: PluginContext): Promise<SearchCandidate[]> {
  const page = await ctx.http.get(`/Search.html?q=${encodeURIComponent(query.trim())}`);
  const root = await page.html();
  throwIfSiteMoved(root, ctx);
  const listElement = root.querySelector("#content_list");
  if (!listElement) return [];

  const candidates: SearchCandidate[] = [];
  for (const item of listElement.querySelectorAll("div[onclick]")) {
    const candidate = candidateOf(item);
    if (candidate) candidates.push(candidate);
  }
  return candidates;
}

/**
 * Since November 2026 every kinoz.to page is a meta refresh to an unrelated site (kion.ru):
 * the site is gone, which is "offline", not a search without results. A mirror set as the
 * plugin's Domain is used as long as it still serves the catalog.
 */
function throwIfSiteMoved(root: HTMLElement, ctx: PluginContext): void {
  const refresh = root.querySelector('meta[http-equiv="refresh" i]')?.getAttribute("content") || "";
  const target = /url\s*=\s*['"]?([^'"\s]+)/i.exec(refresh)?.[1];
  if (!target) return;
  const site = domainOf(ctx.plugin.base_url || "");
  if (domainOf(target) === site) return;
  throw new PluginSourceError("source_offline", `${site} now redirects to ${domainOf(target)}; the site is gone`);
}

function candidateOf(item: HTMLElement): SearchCandidate | undefined {
  const onclick = item.getAttribute("onclick") || "";
  const hrefMatch = onclick.match(/location\.href='(\/Stream\/[^']+\.html)'/);
  if (!hrefMatch) return undefined;

  const titleElement = item.querySelector("div.Opt.leftOpt.Headlne a");
  const title = (titleElement?.getAttribute("title") || "").trim();
  if (!title) return undefined;
  return { title, slug: slugFromHref(hrefMatch[1]), sourcePath: hrefMatch[1] };
}
