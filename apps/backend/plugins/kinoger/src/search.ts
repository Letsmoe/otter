import type { PluginContext } from "@neoworks-dev/otter-sdk";
import type { HTMLElement } from "node-html-parser";

const MAX_SEARCH_RESULTS = 20;

export interface KinogerSearchEntry {
  title: string;
  /** Site-relative link of the title's page, e.g. "/stream/inception.html". */
  href: string;
  /** Kinoger files series under a "Serie" category, which is not always reliable. */
  isSeries: boolean;
}

/** The search form's URL: spaces become "+", as the site expects. */
function searchPath(query: string): string {
  const encodedQuery = encodeURIComponent(query.trim()).replace(/%20/g, "+");
  return `/index.php?do=search&subaction=search&search_start=0&full_search=0&result_from=1&story=${encodedQuery}`;
}

// Kinoger appends " Film" to movie titles in its result list, sometimes before the year.
function cleanTitle(rawTitle: string): string {
  return rawTitle.trim().replace(/\s+Film(\s*\(\d{4}\))?\s*$/i, "").trim();
}

function isSeriesEntry(rawTitle: string, categoryBox: HTMLElement | null): boolean {
  if (/staffel/i.test(rawTitle)) return true;
  if (!categoryBox) return false;
  const categoryLinks = categoryBox.querySelectorAll("li.category a");
  return categoryLinks.some((link) => (link.getAttribute("href") || "").includes("/serie/"));
}

/**
 * Each result is a `div.titlecontrol` (title and link) followed by its
 * `div.general_box` (categories). Entries without a `/stream/` link are skipped.
 */
function entryOf(titleControl: HTMLElement): KinogerSearchEntry | undefined {
  const anchor = titleControl.querySelector("div.title a");
  if (!anchor) return undefined;
  const href = anchor.getAttribute("href") || "";
  if (!href.includes("/stream/")) return undefined;

  const rawTitle = anchor.text.trim();
  const categoryBox = titleControl.nextElementSibling;
  return { title: cleanTitle(rawTitle), href, isSeries: isSeriesEntry(rawTitle, categoryBox) };
}

export async function searchKinoger(query: string, ctx: PluginContext): Promise<KinogerSearchEntry[]> {
  if (query.trim() === "") return [];
  const page = await ctx.http.get(searchPath(query));
  const root = await page.html();
  const entries: KinogerSearchEntry[] = [];
  for (const titleControl of root.querySelectorAll("div.titlecontrol")) {
    const entry = entryOf(titleControl);
    if (entry) entries.push(entry);
  }
  return entries.slice(0, MAX_SEARCH_RESULTS);
}
