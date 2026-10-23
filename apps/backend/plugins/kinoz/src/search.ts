import type { HTMLElement } from "node-html-parser";
import type { PluginContext } from "@neoworks-dev/otter-sdk";
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
  const listElement = (await page.html()).querySelector("#content_list");
  if (!listElement) return [];

  const candidates: SearchCandidate[] = [];
  for (const item of listElement.querySelectorAll("div[onclick]")) {
    const candidate = candidateOf(item);
    if (candidate) candidates.push(candidate);
  }
  return candidates;
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
