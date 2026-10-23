import { describe, expect, test } from "bun:test";
import { episode, fixtures, movie, testPlugin } from "@neoworks-dev/otter-sdk/testing";
import plugin from "../plugin.ts";

const fixtureDirectory = `${import.meta.dir}/fixtures`;
const subject = testPlugin(plugin, { fetch: fixtures(fixtureDirectory) });
const MATRIX = movie({ title: "The Matrix", year: 1999, tmdb_id: 603, imdb_id: "tt0133093" });
const BREAKING_BAD = episode({ series: "Breaking Bad", tmdb_id: 1396, imdb_id: "tt0903747" }, 1, 1);

describe("opensubtitles-stremio (offline)", () => {
  test("declares only the subtitles capability", () => {
    expect(plugin.subtitles).toBeDefined();
    expect(plugin.streams).toBeUndefined();
    expect(plugin.sources).toBeUndefined();
  });

  test("a movie maps ISO 639-2 codes to ISO 639-1 and takes the release name", async () => {
    const list = await subject.subtitles.list({ media: MATRIX, languages: ["de"] });
    expect(list.data.length).toBeGreaterThan(0);
    for (const subtitle of list.data) {
      expect(subtitle.language).toBe("de");
      expect(subtitle.format).toBe("srt");
      expect(subtitle.url).toStartWith("https://subs5.strem.io/");
      expect(subtitle.release).toBeTruthy();
    }
  });

  test("without languages every known language comes back", async () => {
    const list = await subject.subtitles.list({ media: MATRIX, languages: [] });
    const languages = new Set(list.data.map((subtitle) => subtitle.language));
    expect(languages.has("de")).toBe(true);
    expect(languages.has("el")).toBe(true);
    expect(languages.has("pt")).toBe(true);
  });

  test("an episode looks up series, season and episode", async () => {
    const list = await subject.subtitles.list({ media: BREAKING_BAD, languages: ["en", "de"] });
    expect(new Set(list.data.map((subtitle) => subtitle.language))).toEqual(new Set(["en", "de"]));
  });

  test("keeps at most ten files per language", async () => {
    const entries = Array.from({ length: 14 }, (_, index) => ({ id: String(index), url: `https://x.example/${index}`, lang: "eng", subtitleFileName: `f${index}.srt` }));
    const crowded = testPlugin(plugin, {
      fetch: (async () => new Response(JSON.stringify({ subtitles: entries }))) as unknown as typeof fetch,
    });
    const list = await crowded.subtitles.list({ media: MATRIX, languages: [] });
    expect(list.data).toHaveLength(10);
  });

  test("a title without an IMDb id has no subtitles and costs no request", async () => {
    let requests = 0;
    const counting = testPlugin(plugin, {
      fetch: (async () => {
        requests += 1;
        return new Response("{}");
      }) as unknown as typeof fetch,
    });
    const list = await counting.subtitles.list({ media: movie({ title: "Unknown", tmdb_id: 5 }), languages: [] });
    expect(list.data).toEqual([]);
    expect(requests).toBe(0);
  });

  test("falls back to the file name when the release is missing", async () => {
    const bare = testPlugin(plugin, {
      fetch: (async () =>
        new Response(JSON.stringify({ subtitles: [{ id: "1", url: "https://x.example/1", lang: "ger", subtitleFileName: "Movie.srt" }] }))) as unknown as typeof fetch,
    });
    const list = await bare.subtitles.list({ media: MATRIX, languages: [] });
    expect(list.data[0].release).toBe("Movie.srt");
  });
});
