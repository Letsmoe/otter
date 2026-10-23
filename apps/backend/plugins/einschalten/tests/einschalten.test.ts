import { describe, expect, test } from "bun:test";
import { fixtures, movie, episode, testPlugin } from "@neoworks-dev/otter-sdk/testing";
import plugin from "../plugin.ts";

const subject = testPlugin(plugin, { fetch: fixtures(`${import.meta.dir}/fixtures`) });

describe("einschalten (offline)", () => {
  test("a movie lists its watch link with the quality of the release", async () => {
    const streams = await subject.streams.list({ media: movie({ title: "Matrix", year: 1999, tmdb_id: 603 }) });
    expect(streams.data).toEqual([
      { object: "stream", label: "Doodstream", url: "https://vide0.net/e/u47sxo1mqsco", quality: "DVDRip", language: "de" },
    ]);
  });

  test("a movie the site does not have is an empty list", async () => {
    const streams = await subject.streams.list({ media: movie({ title: "Unknown", tmdb_id: 1 }) });
    expect(streams.data).toEqual([]);
  });

  test("episodes are an empty list (the site has films only)", async () => {
    const streams = await subject.streams.list({ media: episode({ series: "Matrix", tmdb_id: 603 }, 1, 1) });
    expect(streams.data).toEqual([]);
  });
});
