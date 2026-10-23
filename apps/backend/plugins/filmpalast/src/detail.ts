import type { HTMLElement } from "node-html-parser";
import type { StreamInput } from "@neoworks-dev/otter-sdk";

/** The player links of a stream page, de-duplicated by URL. */
export function linksOf(root: HTMLElement): StreamInput[] {
  const seenUrls = new Set<string>();
  const streams: StreamInput[] = [];
  for (const link of root.querySelectorAll("a.button.rb")) {
    const url = playerUrlOf(link);
    if (!url || seenUrls.has(url)) continue;
    seenUrls.add(url);
    streams.push(streamOf(link, url));
  }
  return streams;
}

// Current pages keep the player in data-player-url (opened by embedy()); older ones use a plain href.
function playerUrlOf(link: HTMLElement): string {
  const href = link.getAttribute("href") || "";
  if (href.startsWith("http")) return href;
  return link.getAttribute("data-player-url") || "";
}

function streamOf(link: HTMLElement, url: string): StreamInput {
  const label = labelOf(link, url);
  const stream: StreamInput = { url, label, language: "de" };
  if (isHd(label)) stream.quality = "HD";
  return stream;
}

function labelOf(link: HTMLElement, url: string): string {
  const label = listItemTextOf(link).replace(/\bPlay\b/gi, "").trim();
  if (label) return label;
  return providerOf(url);
}

function listItemTextOf(link: HTMLElement): string {
  const listItem = link.closest("li");
  if (!listItem) return "";
  return listItem.text.trim();
}

function isHd(label: string): boolean {
  return /\bHD\b/i.test(label);
}

function providerOf(url: string): string {
  try {
    return new URL(url).hostname.replace(/^www\./, "").split(".")[0];
  } catch {
    return "unknown";
  }
}
