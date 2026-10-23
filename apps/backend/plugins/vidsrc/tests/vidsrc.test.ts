import { describe, expect, test } from "bun:test";
import { episode, fixtures, movie, testPlugin } from "@neoworks-dev/otter-sdk/testing";
import plugin from "../plugin.ts";

const fixtureDirectory = `${import.meta.dir}/fixtures`;
const MOVIE_LOOKUP = "https://vidsrc.sh/vs_src.php?type=movie&id=603";
const EPISODE_LOOKUP = "https://vidsrc.sh/vs_src.php?type=tv&id=60585&season=3&episode=3";
const SERVER_TOKEN = "fake-token-1";

/** Answers the first `count` generate.php calls with 429, then serves the fixtures. */
function rateLimitedGenerate(count: number): typeof fetch {
  const serve = fixtures(fixtureDirectory);
  let remaining = count;
  return (async (input: RequestInfo | URL, init?: RequestInit) => {
    if (String(input).endsWith("/generate.php") && remaining > 0) {
      remaining -= 1;
      return new Response("rate limited", { status: 429 });
    }
    return serve(input, init);
  }) as typeof fetch;
}

async function decryptedStreamUrls(): Promise<string[]> {
  const text = await Bun.file(`${fixtureDirectory}/expected-stream-urls.txt`).text();
  return text.split("\n").filter((line) => line.length > 0);
}

describe("vidsrc (offline)", () => {
  test("a movie yields one lookup stream", async () => {
    const subject = testPlugin(plugin, { fetch: fixtures(fixtureDirectory) });
    const streams = await subject.streams.list({ media: movie({ title: "The Matrix", year: 1999, tmdb_id: 603 }) });
    expect(streams.data).toEqual([{ object: "stream", url: MOVIE_LOOKUP, label: "VidSrc", language: "en" }]);
  });

  test("an episode passes type, season and episode to the lookup", async () => {
    const subject = testPlugin(plugin, { fetch: fixtures(fixtureDirectory) });
    const streams = await subject.streams.list({
      media: episode({ series: "Breaking Bad", year: 2008, tmdb_id: 1396 }, 1, 1),
    });
    expect(streams.data.map((stream) => stream.url)).toEqual([
      "https://vidsrc.sh/vs_src.php?type=tv&id=1396&season=1&episode=1",
    ]);
  });

  test("a title without a TMDB id is an empty list", async () => {
    const subject = testPlugin(plugin, { fetch: fixtures(fixtureDirectory) });
    const streams = await subject.streams.list({ media: movie({ title: "The Matrix", year: 1999 }) });
    expect(streams.data).toEqual([]);
  });

  test("sources.retrieve runs the chain and returns the tokenized first server", async () => {
    const subject = testPlugin(plugin, { fetch: fixtures(fixtureDirectory) });
    const [firstServer] = await decryptedStreamUrls();
    const source = await subject.sources.retrieve({ url: MOVIE_LOOKUP });
    expect(source.url).toBe(`${firstServer}?token=${SERVER_TOKEN}`);
    expect(source.mime_type).toBe("application/x-mpegURL");
    expect(source.headers).toEqual({
      Referer: "https://stellarconductornexus.com/",
      Origin: "https://stellarconductornexus.com",
    });
  });

  test("an episode asks the stream API built from the series' streamBase", async () => {
    const serve = fixtures(fixtureDirectory);
    const requested: string[] = [];
    const recordingFetch = (async (input: RequestInfo | URL, init?: RequestInit) => {
      requested.push(String(input));
      return serve(input, init);
    }) as typeof fetch;
    const subject = testPlugin(plugin, { fetch: recordingFetch });
    const [firstServer] = await decryptedStreamUrls();
    const source = await subject.sources.retrieve({ url: EPISODE_LOOKUP });
    const apiRequest = requested.find((url) => url.startsWith("https://data.vidsrc.sh/api.php"));
    expect(apiRequest).toStartWith("https://data.vidsrc.sh/api.php?type=tv&tmdb=60585&season=3&episode=3&stream_urls&api_token=");
    expect(source.url).toBe(`${firstServer}?token=${SERVER_TOKEN}`);
  });

  test("a rate-limited first server falls through to the second", async () => {
    const subject = testPlugin(plugin, { fetch: rateLimitedGenerate(1) });
    const [, secondServer] = await decryptedStreamUrls();
    const source = await subject.sources.retrieve({ url: MOVIE_LOOKUP });
    expect(source.url).toBe(`${secondServer}?token=${SERVER_TOKEN}`);
  });

  test("every server failing is source_offline", async () => {
    const subject = testPlugin(plugin, { fetch: rateLimitedGenerate(3) });
    await expect(subject.sources.retrieve({ url: MOVIE_LOOKUP })).rejects.toMatchObject({ code: "source_offline" });
  });
});
