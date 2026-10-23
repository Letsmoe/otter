import { describe, expect, test } from "bun:test";
import { episode, fixtures, movie, testPlugin } from "@neoworks-dev/otter-sdk/testing";
import plugin from "../plugin.ts";

const subject = testPlugin(plugin, { fetch: fixtures(`${import.meta.dir}/fixtures`) });

describe("aniworld (offline)", () => {
  test("lists the German, German-subtitled and English-subtitled hosters of an episode", async () => {
    const streams = await subject.streams.list({
      media: episode({ series: "Attack on Titan", original_title: "Shingeki no Kyojin", year: 2013, tmdb_id: 1429 }, 1, 1),
    });
    expect(streams.data.length).toBe(12);
    expect(streams.data[0]).toMatchObject({
      url: "https://aniworld.to/redirect/3540458",
      label: "VOE — Deutsch",
      language: "de",
    });
    expect(streams.data.map((stream) => stream.language)).toContain("de-sub");
    expect(streams.data.map((stream) => stream.language)).toContain("en-sub");
    expect(streams.data.find((stream) => stream.language === "en-sub")?.label).toBe("VOE — Untertitel Englisch");
  });

  test("matches a series by its alternative title", async () => {
    const streams = await subject.streams.list({
      media: episode({ series: "Demon Slayer", original_title: "Kimetsu no Yaiba", year: 2019, tmdb_id: 85937 }, 1, 1),
    });
    expect(streams.data.length).toBeGreaterThan(0);
    expect(streams.data[0].url).toStartWith("https://aniworld.to/redirect/");
  });

  test("a series not on the site is an empty list", async () => {
    const streams = await subject.streams.list({ media: episode({ series: "Unknown Show" }, 1, 1) });
    expect(streams.data).toEqual([]);
  });

  test("an episode the site does not have is an empty list", async () => {
    const streams = await subject.streams.list({
      media: episode({ series: "Attack on Titan", tmdb_id: 1429 }, 1, 999),
    });
    expect(streams.data).toEqual([]);
  });

  test("movies are an empty list (films are not matched to TMDB movies)", async () => {
    const streams = await subject.streams.list({ media: movie({ title: "Attack on Titan", tmdb_id: 1 }) });
    expect(streams.data).toEqual([]);
  });
});
