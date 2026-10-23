import { toIso6391, toIso6392 } from "@neoworks-dev/otter-sdk";
import type { List, PluginContext, SubtitleInput, SubtitleListParams } from "@neoworks-dev/otter-sdk";
import { DOWNLOAD_HEADERS, MAX_LANGUAGES, MAX_PER_LANGUAGE, SEARCH_HEADERS } from "./constants.ts";
import { searchUrlOf } from "./target.ts";

interface SearchEntry {
  SubDownloadLink?: string;
  SubFormat?: string;
  SubLanguageID?: string;
  /** ISO 639-1. */
  ISO639?: string;
  SubFileName?: string;
  MovieReleaseName?: string;
  SubHearingImpaired?: string;
  SubAutoTranslation?: string;
  SubBad?: string;
}

/** Lists the API's files for a title, at most MAX_PER_LANGUAGE for each wanted language. */
export async function listSubtitles({ media, languages }: SubtitleListParams, ctx: PluginContext): Promise<List<SubtitleInput>> {
  const searches = languageCodesOf(languages).map((code) => searchLanguage(media, code, ctx));
  const results = await Promise.all(searches);
  return ctx.list(results.flat());
}

/** Three-letter codes of the wanted languages; "all" asks for every language. */
function languageCodesOf(languages: string[]): string[] {
  if (languages.length === 0) return ["all"];
  const codes = languages.map(toIso6392).filter((code): code is string => code !== undefined);
  return codes.slice(0, MAX_LANGUAGES);
}

async function searchLanguage(media: SubtitleListParams["media"], languageCode: string, ctx: PluginContext): Promise<SubtitleInput[]> {
  const searchUrl = searchUrlOf(media, languageCode);
  if (!searchUrl) return [];
  const response = await ctx.http.get(searchUrl, { headers: SEARCH_HEADERS });
  const entries = await response.json<SearchEntry[]>();
  if (!Array.isArray(entries)) return [];
  return limitPerLanguage(entries.flatMap(toInput));
}

function toInput(entry: SearchEntry): SubtitleInput[] {
  if (!isUsable(entry)) return [];
  const language = toIso6391(entry.ISO639 || entry.SubLanguageID || "");
  if (!language) return [];
  return [
    {
      url: entry.SubDownloadLink as string,
      language,
      format: "srt",
      release: releaseOf(entry),
      hearing_impaired: entry.SubHearingImpaired === "1",
      headers: DOWNLOAD_HEADERS,
    },
  ];
}

function isUsable(entry: SearchEntry): boolean {
  if (!entry.SubDownloadLink) return false;
  if (entry.SubFormat !== "srt") return false;
  return entry.SubAutoTranslation !== "1" && entry.SubBad !== "1";
}

function releaseOf(entry: SearchEntry): string | undefined {
  const release = (entry.MovieReleaseName || "").trim();
  if (release) return release;
  return entry.SubFileName;
}

function limitPerLanguage(inputs: SubtitleInput[]): SubtitleInput[] {
  const counts = new Map<string, number>();
  return inputs.filter((input) => {
    const count = counts.get(input.language) || 0;
    counts.set(input.language, count + 1);
    return count < MAX_PER_LANGUAGE;
  });
}
