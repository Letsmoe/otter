import { describe, expect, test } from "bun:test";
import { episode, fixtures, movie, testPlugin } from "@neoworks-dev/otter-sdk/testing";
import plugin from "../plugin.ts";

const fixtureDirectory = `${import.meta.dir}/fixtures`;
const subject = testPlugin(plugin, { fetch: fixtures(fixtureDirectory) });

/** The fixtures answer 200; this one answers the API's real 502 for titles it does not have. */
const offlineFetch = (async (input: RequestInfo | URL, init?: RequestInit) => {
  const response = await fixtures(fixtureDirectory)(input, init);
  return new Response(await response.text(), { status: 502, headers: { "content-type": "application/json" } });
}) as typeof fetch;
const offlineSubject = testPlugin(plugin, { fetch: offlineFetch });

describe("aurorascreen (offline)", () => {
  test("a movie yields one stream per server that has HLS, linking the lookup", async () => {
    const streams = await subject.streams.list({ media: movie({ title: "The Matrix", year: 1999, tmdb_id: 603 }) });
    expect(streams.data).toEqual([
      {
        object: "stream",
        url: "https://stream.aurorascreen.org/jupiter?id=603&name=The%20Matrix&year=1999",
        label: "AuroraScreen Jupiter",
        quality: "2160p",
        language: "en",
      },
      {
        object: "stream",
        url: "https://stream.aurorascreen.org/venus?id=603&name=The%20Matrix&year=1999",
        label: "AuroraScreen Venus",
        language: "en",
      },
    ]);
  });

  test("an episode passes season and episode to the lookup", async () => {
    const streams = await subject.streams.list({
      media: episode({ series: "Breaking Bad", year: 2008, tmdb_id: 1396 }, 1, 1),
    });
    expect(streams.data.map((stream) => stream.url)).toEqual([
      "https://stream.aurorascreen.org/jupiter?id=1396&name=Breaking%20Bad&year=2008&s=1&e=1",
      "https://stream.aurorascreen.org/venus?id=1396&name=Breaking%20Bad&year=2008&s=1&e=1",
    ]);
  });

  test("a title the servers do not have is an empty list", async () => {
    const streams = await subject.streams.list({ media: movie({ title: "Nothing", year: 1990, tmdb_id: 1 }) });
    expect(streams.data).toEqual([]);
  });

  test("a movie without a TMDB id is an empty list", async () => {
    const streams = await subject.streams.list({ media: movie({ title: "The Matrix", year: 1999 }) });
    expect(streams.data).toEqual([]);
  });

  test("sources.retrieve returns the best HLS url with the site's Referer", async () => {
    const source = await subject.sources.retrieve({
      url: "https://stream.aurorascreen.org/jupiter?id=603&name=The%20Matrix&year=1999",
    });
    expect(source.url).toStartWith("https://stream.aurorascreen.org/jpl?url=");
    expect(source.mime_type).toBe("application/x-mpegURL");
    expect(source.quality).toBe("2160p");
    expect(source.headers).toEqual({ Referer: "https://aurorascreen.org/" });
  });

  test("sources.retrieve on a 502 is source_offline", async () => {
    const lookup = "https://stream.aurorascreen.org/venus?id=1&name=Nothing&year=1990";
    await expect(offlineSubject.sources.retrieve({ url: lookup })).rejects.toMatchObject({ code: "source_offline" });
  });
});
