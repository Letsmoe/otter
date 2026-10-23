import type { HTMLElement } from "node-html-parser";

export interface SuggestedTitle {
  title: string;
  /** Absolute movie page URL. */
  url: string;
}

export interface ProviderChip {
  /** Position among all chips of the page; the stream URL's fragment refers to it. */
  index: number;
  provider: string;
  language?: string;
  /** The encrypted link token the page sends to the mint endpoint. */
  playToken: string;
}

const LANGUAGE_BY_LABEL: Record<string, string> = {
  Deutsch: "de",
  English: "en",
};
const YEAR = /^\d{4}$/;

export function suggestedTitlesOf(body: { movies?: SuggestedTitle[] }): SuggestedTitle[] {
  return body.movies || [];
}

/** The year is one of the metadata labels, e.g. `<span class="ft-meta-label">1999</span>`. */
export function yearOf(root: HTMLElement): number | undefined {
  const label = root.querySelectorAll(".ft-meta-label").find((element) => YEAR.test(element.text.trim()));
  if (!label) return undefined;
  return Number(label.text.trim());
}

export function csrfTokenOf(root: HTMLElement): string {
  return root.querySelector('meta[name="csrf-token"]')?.getAttribute("content") || "";
}

/** Every provider chip, in page order, with the language of the row it sits in. */
export function providerChipsOf(root: HTMLElement): ProviderChip[] {
  const chips: ProviderChip[] = [];
  for (const row of root.querySelectorAll(".provider-row")) {
    const language = languageOfRow(row);
    for (const chip of row.querySelectorAll(".provider-chip[data-p]")) {
      chips.push({
        index: chips.length,
        provider: chip.getAttribute("aria-label") || "",
        language,
        playToken: chip.getAttribute("data-p") || "",
      });
    }
  }
  return chips;
}

function languageOfRow(row: HTMLElement): string | undefined {
  const label = row.querySelector(".provider-row__lang")?.text.trim() || "";
  return LANGUAGE_BY_LABEL[label];
}

/** The stream URL of a chip: the movie page, with the chip's index as fragment. */
export function chipStreamUrl(pageUrl: string, chipIndex: number): string {
  return `${pageUrl}#chip=${chipIndex}`;
}

export function pageUrlOf(streamUrl: string): string {
  return streamUrl.split("#")[0];
}

export function chipIndexOf(streamUrl: string): number | undefined {
  const match = streamUrl.match(/#chip=(\d+)$/);
  if (!match) return undefined;
  return Number(match[1]);
}
