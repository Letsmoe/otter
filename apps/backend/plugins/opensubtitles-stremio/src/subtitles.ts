import { toIso6391 } from "@neoworks-dev/otter-sdk";
import type { List, PluginContext, SubtitleInput, SubtitleListParams } from "@neoworks-dev/otter-sdk";
import { MAX_PER_LANGUAGE } from "./constants.ts";
import { lookupUrlOf } from "./target.ts";

interface AddonSubtitle {
  url: string;
  /** ISO 639-2, e.g. "ger". */
  lang: string;
  subtitleFileName?: string;
  movieReleaseName?: string;
}

interface AddonResponse {
  subtitles?: AddonSubtitle[];
}

/** Lists the addon's files for a title, at most MAX_PER_LANGUAGE per wanted language. */
export async function listSubtitles({ media, languages }: SubtitleListParams, ctx: PluginContext): Promise<List<SubtitleInput>> {
  const lookupUrl = lookupUrlOf(media);
  if (!lookupUrl) return ctx.list<SubtitleInput>([]);
  const response = await ctx.http.get(lookupUrl, { headers: { Accept: "application/json" }, allow_error_status: true });
  if (response.status === 404) return ctx.list<SubtitleInput>([]);
  const body = await response.json<AddonResponse>();
  const inputs = (body.subtitles || []).flatMap(toInput);
  return ctx.list(limitPerLanguage(onlyLanguages(inputs, languages)));
}

function toInput(entry: AddonSubtitle): SubtitleInput[] {
  const language = toIso6391(entry.lang);
  if (!language || !entry.url) return [];
  return [
    {
      url: entry.url,
      language,
      format: "srt",
      release: entry.movieReleaseName || entry.subtitleFileName,
    },
  ];
}

function onlyLanguages(inputs: SubtitleInput[], languages: string[]): SubtitleInput[] {
  if (languages.length === 0) return inputs;
  return inputs.filter((input) => languages.includes(input.language));
}

function limitPerLanguage(inputs: SubtitleInput[]): SubtitleInput[] {
  const counts = new Map<string, number>();
  return inputs.filter((input) => {
    const count = counts.get(input.language) || 0;
    counts.set(input.language, count + 1);
    return count < MAX_PER_LANGUAGE;
  });
}
