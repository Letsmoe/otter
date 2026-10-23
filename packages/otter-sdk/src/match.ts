import type { MediaRef, TitleFields } from "./model.ts";

// Letters NFD cannot split into base letter and accent.
const SPECIAL_LETTERS: Record<string, string> = {
  ä: "ae",
  ö: "oe",
  ü: "ue",
  ß: "ss",
  æ: "ae",
  œ: "oe",
  ø: "o",
  đ: "d",
  ł: "l",
  ð: "d",
  þ: "th",
  ı: "i",
};

const SPECIAL_LETTER_PATTERN = /[äöüßæœøđłðþı]/g;
const COMBINING_MARKS = /[\u0300-\u036f]/g;
const APOSTROPHES = /['\u2018\u2019`\u00b4]/g;
// Whitespace, ASCII punctuation, and the Latin-1 and general punctuation blocks.
const SEPARATORS = /[\s\u00a0-\u00bf\u2000-\u206f!-\/:-@\[-`{-~]+/g;
const TRAILING_YEAR = /\s*\(\d{4}\)\s*$/;
const FILLER_WORDS = ["and", "und"];
const LEADING_ARTICLES = ["the", "der", "die", "das"];

/**
 * Lowercases, folds umlauts (ä -> ae, ß -> ss) and other diacritics (é -> e),
 * drops apostrophes and collapses any other punctuation to single spaces.
 */
export function normalize(title: string): string {
  const lowered = title.toLowerCase();
  const withSpecials = lowered.replace(SPECIAL_LETTER_PATTERN, (letter) => SPECIAL_LETTERS[letter]);
  const unaccented = withSpecials.normalize("NFD").replace(COMBINING_MARKS, "");
  const withoutApostrophes = unaccented.replace(APOSTROPHES, "");
  return withoutApostrophes.replace(SEPARATORS, " ").trim();
}

/** Normalizes a title for comparing: no trailing "(1999)", no leading article, "&"/"and"/"und" dropped. */
function comparableTitle(title: string): string {
  const words = normalize(title.replace(TRAILING_YEAR, "")).split(" ");
  const withoutFillers = words.filter((word) => !FILLER_WORDS.includes(word));
  if (withoutFillers.length > 1 && LEADING_ARTICLES.includes(withoutFillers[0])) {
    withoutFillers.shift();
  }
  return withoutFillers.join(" ");
}

function titleFieldsOf(media: MediaRef): TitleFields {
  if (media.object === "movie") return media;
  return media.series;
}

function comparableTitlesOf(media: MediaRef): string[] {
  const fields = titleFieldsOf(media);
  const known = [fields.title, fields.original_title];
  const comparable: string[] = [];
  for (const title of known) {
    if (title) comparable.push(comparableTitle(title));
  }
  return comparable;
}

/** Whether a title found on a site is the movie, or the series of the episode, being looked for. */
export function titles(candidateTitle: string, media: MediaRef): boolean {
  const candidate = comparableTitle(candidateTitle);
  if (candidate === "") return false;
  return comparableTitlesOf(media).includes(candidate);
}

export interface BestOptions<Candidate> {
  /** Reads a candidate's title; defaults to the candidate itself (string) or its `title`. */
  title?: (candidate: Candidate) => string;
  year?: (candidate: Candidate) => number | undefined;
}

function defaultTitleOf(candidate: unknown): string {
  if (typeof candidate === "string") return candidate;
  const title = (candidate as { title?: unknown }).title;
  if (typeof title === "string") return title;
  return "";
}

/** 0 is the best fit: same year, then one year off, then unknown, then further away. */
function yearRank(candidateYear: number | undefined, wantedYear: number | undefined): number {
  if (candidateYear === undefined || wantedYear === undefined) return 2;
  const distance = Math.abs(candidateYear - wantedYear);
  if (distance === 0) return 0;
  if (distance === 1) return 1;
  return 3;
}

/**
 * The candidate whose normalized title equals the media's title or original
 * title. The year only decides between several such candidates.
 */
export function best<Candidate>(
  candidates: Candidate[],
  media: MediaRef,
  options: BestOptions<Candidate> = {},
): Candidate | undefined {
  const titleOf = options.title || defaultTitleOf;
  const matching = candidates.filter((candidate) => titles(titleOf(candidate), media));
  if (matching.length === 0) return undefined;
  if (!options.year) return matching[0];

  const wantedYear = titleFieldsOf(media).year;
  const yearOf = options.year;
  let winner = matching[0];
  for (const candidate of matching) {
    if (yearRank(yearOf(candidate), wantedYear) < yearRank(yearOf(winner), wantedYear)) {
      winner = candidate;
    }
  }
  return winner;
}

export const match = { normalize, titles, best };
export type Matcher = typeof match;
