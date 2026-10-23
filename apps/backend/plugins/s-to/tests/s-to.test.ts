import { describe, expect, test } from "bun:test";
import { episode, fixtures, movie, testPlugin } from "@neoworks-dev/otter-sdk/testing";
import plugin from "../plugin.ts";

const subject = testPlugin(plugin, { fetch: fixtures(`${import.meta.dir}/fixtures`) });

describe("s-to (offline)", () => {
  test("lists the German and subtitled links of an episode", async () => {
    const streams = await subject.streams.list({ media: episode({ series: "Dark", tmdb_id: 70523 }, 1, 1) });
    expect(streams.data.map((stream) => stream.label)).toEqual(["VOE — Deutsch", "Vidoza — Untertitel"]);
    expect(streams.data.map((stream) => stream.language)).toEqual(["de", "de-sub"]);
    expect(streams.data[0].url).toBe("https://serienstream.to/r?t=abc123");
  });

  test("matches titles with umlauts", async () => {
    const streams = await subject.streams.list({ media: episode({ series: "Ueber den Dingen" }, 1, 1) });
    expect(streams.data.map((stream) => stream.url)).toEqual(["https://serienstream.to/r?t=umlaut1"]);
  });

  test("a series not on the site is an empty list", async () => {
    const streams = await subject.streams.list({ media: episode({ series: "Unknown Show" }, 1, 1) });
    expect(streams.data).toEqual([]);
  });

  test("an episode the site does not have is an empty list", async () => {
    const streams = await subject.streams.list({ media: episode({ series: "Dark" }, 9, 9) });
    expect(streams.data).toEqual([]);
  });

  test("movies are an empty list (s.to is series-only)", async () => {
    const streams = await subject.streams.list({ media: movie({ title: "Dark", tmdb_id: 1 }) });
    expect(streams.data).toEqual([]);
  });
});
