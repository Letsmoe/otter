import { describe, expect, test } from "bun:test";
import { episode, fixtures, movie, testPlugin } from "@neoworks-dev/otter-sdk/testing";
import plugin from "../plugin.ts";

const subject = testPlugin(plugin, { fetch: fixtures(`${import.meta.dir}/fixtures`) });

describe("kinoking (offline)", () => {
  test("a movie lists its hoster links, found by TMDB id through the search", async () => {
    const streams = await subject.streams.list({ media: movie({ title: "The Matrix", original_title: "The Matrix", year: 1999, tmdb_id: 603 }) });
    expect(streams.data.length).toBeGreaterThan(10);
    expect(streams.data[0]).toMatchObject({ url: "https://dood.to/e/kcgfx3zmb59l", language: "de" });
  });

  test("the movie's links skip the embed frontends and keep the English group's language", async () => {
    const streams = await subject.streams.list({ media: movie({ title: "Matrix", tmdb_id: 603 }) });
    const urls = streams.data.map((stream) => stream.url);
    expect(urls.some((url) => url.includes("meinecloud.click") || url.includes("vidsync.xyz") || url.includes("cinesrc.st"))).toBe(false);
    expect(streams.data.map((stream) => stream.language)).toContain("en");
    expect(urls).toContain("https://dood.yt/w/rCsidzQRvK");
  });

  test("an episode lists the links of the series' episode page, by TMDB id", async () => {
    const streams = await subject.streams.list({
      media: episode({ series: "Breaking Bad", tmdb_id: 1396 }, 1, 1),
    });
    expect(streams.data.length).toBe(20);
    expect(streams.data[0]).toMatchObject({ url: "https://voe.sx/e/yvcyfo1zfcev", language: "de" });
  });

  test("a movie the search does not find is an empty list", async () => {
    const streams = await subject.streams.list({ media: movie({ title: "zzzqqq", tmdb_id: 603 }) });
    expect(streams.data).toEqual([]);
  });

  test("a movie without a TMDB id is an empty list", async () => {
    const streams = await subject.streams.list({ media: movie({ title: "Matrix" }) });
    expect(streams.data).toEqual([]);
  });
});
