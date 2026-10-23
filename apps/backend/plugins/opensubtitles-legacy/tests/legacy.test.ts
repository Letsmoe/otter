import { describe, expect, test } from "bun:test";
import { episode, fixtures, movie, testPlugin } from "@neoworks-dev/otter-sdk/testing";
import plugin from "../plugin.ts";

const fixtureDirectory = `${import.meta.dir}/fixtures`;
const MATRIX = movie({ title: "The Matrix", year: 1999, tmdb_id: 603, imdb_id: "tt0133093" });
const BREAKING_BAD = episode({ series: "Breaking Bad", tmdb_id: 1396, imdb_id: "tt0903747" }, 1, 1);

/** Records the requested URLs and headers, then serves the fixtures. */
function recordingFetch() {
  const requests: Array<{ url: string; headers: Record<string, string> }> = [];
  const serve = fixtures(fixtureDirectory);
  const recording = (async (input: RequestInfo | URL, init?: RequestInit) => {
    requests.push({ url: String(input), headers: (init?.headers || {}) as Record<string, string> });
    return serve(input, init);
  }) as typeof fetch;
  return { requests, recording };
}

describe("opensubtitles-legacy (offline)", () => {
  test("declares only the subtitles capability", () => {
    expect(plugin.subtitles).toBeDefined();
    expect(plugin.streams).toBeUndefined();
    expect(plugin.sources).toBeUndefined();
  });

  test("a movie is searched by padded IMDb number and three-letter language", async () => {
    const { requests, recording } = recordingFetch();
    const list = await testPlugin(plugin, { fetch: recording }).subtitles.list({ media: MATRIX, languages: ["de"] });
    expect(requests.map((request) => request.url)).toEqual([
      "https://rest.opensubtitles.org/search/imdbid-0133093/sublanguageid-ger",
    ]);
    expect(requests[0].headers["X-User-Agent"]).toBe("TemporaryUserAgent");
    expect(requests[0].headers["User-Agent"]).toBe("TemporaryUserAgent");
    expect(list.data).toHaveLength(6);
    expect(list.data[0].language).toBe("de");
    expect(list.data[0].url).toStartWith("https://dl.opensubtitles.org/");
  });

  test("an episode puts episode, series, season in alphabetical path order", async () => {
    const { requests, recording } = recordingFetch();
    const list = await testPlugin(plugin, { fetch: recording }).subtitles.list({ media: BREAKING_BAD, languages: ["de"] });
    expect(requests[0].url).toBe(
      "https://rest.opensubtitles.org/search/episode-1/imdbid-0903747/season-1/sublanguageid-ger",
    );
    expect(list.data).toHaveLength(5);
  });

  test("carries the hearing impaired flag, the release and the download headers", async () => {
    const list = await testPlugin(plugin, { fetch: fixtures(fixtureDirectory) }).subtitles.list({ media: MATRIX, languages: ["de"] });
    expect(list.data.map((subtitle) => subtitle.hearing_impaired)).toEqual([false, false, true, true, true, false]);
    expect(list.data[0].release).toBe("The.Matrix.1999.UHD2BD.1080p.german.srt");
    expect(list.data[0].headers).toEqual({ "User-Agent": "TemporaryUserAgent", "X-User-Agent": "TemporaryUserAgent" });
  });

  test("asks once per language and keeps the order of the profile", async () => {
    const { requests, recording } = recordingFetch();
    const list = await testPlugin(plugin, { fetch: recording }).subtitles.list({ media: MATRIX, languages: ["en", "de"] });
    expect(requests).toHaveLength(2);
    expect(list.data.map((subtitle) => subtitle.language)).toEqual(["en", "en", "en", "en", "de", "de", "de", "de", "de", "de"]);
  });

  test("without languages it asks for all", async () => {
    const urls: string[] = [];
    const emptyAnswer = (async (input: RequestInfo | URL) => {
      urls.push(String(input));
      return new Response("[]");
    }) as unknown as typeof fetch;
    await testPlugin(plugin, { fetch: emptyAnswer }).subtitles.list({ media: MATRIX, languages: [] });
    expect(urls[0]).toEndWith("/sublanguageid-all");
  });

  test("skips files that are not srt, machine translated or flagged bad", async () => {
    const entries = [
      { SubDownloadLink: "https://dl.example/1.gz", SubFormat: "srt", ISO639: "de", SubFileName: "ok.srt" },
      { SubDownloadLink: "https://dl.example/2.gz", SubFormat: "sub", ISO639: "de", SubFileName: "a.sub" },
      { SubDownloadLink: "https://dl.example/3.gz", SubFormat: "srt", ISO639: "de", SubAutoTranslation: "1" },
      { SubDownloadLink: "https://dl.example/4.gz", SubFormat: "srt", ISO639: "de", SubBad: "1" },
    ];
    const fetchEntries = (async () => new Response(JSON.stringify(entries))) as unknown as typeof fetch;
    const list = await testPlugin(plugin, { fetch: fetchEntries }).subtitles.list({ media: MATRIX, languages: ["de"] });
    expect(list.data.map((subtitle) => subtitle.release)).toEqual(["ok.srt"]);
  });

  test("a title without an IMDb id costs no request", async () => {
    const { requests, recording } = recordingFetch();
    const list = await testPlugin(plugin, { fetch: recording }).subtitles.list({
      media: movie({ title: "Unknown", tmdb_id: 5 }),
      languages: ["de"],
    });
    expect(list.data).toEqual([]);
    expect(requests).toHaveLength(0);
  });
});
