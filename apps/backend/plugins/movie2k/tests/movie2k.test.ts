import { describe, expect, test } from "bun:test";
import { episode, fixtures, movie, testPlugin } from "@neoworks-dev/otter-sdk/testing";
import plugin from "../plugin.ts";

const subject = testPlugin(plugin, { fetch: fixtures(`${import.meta.dir}/fixtures`) });

describe("movie2k (offline)", () => {
  test("a movie is found by its IMDb id on the detail page, after the search without the article", async () => {
    const streams = await subject.streams.list({
      media: movie({ title: "The Matrix", original_title: "The Matrix", year: 1999, tmdb_id: 603, imdb_id: "tt0133093" }),
    });
    expect(streams.data.map((stream) => stream.url)).toEqual([
      "https://vinovo.to/e/0pnqerpwckdgkg",
      "https://voe.sx/e/vpmkmsvbwgsm",
    ]);
    expect(streams.data[0]).toMatchObject({ label: "Vinovo", language: "de" });
  });

  test("an episode is found on its season page by the episode id", async () => {
    const streams = await subject.streams.list({
      media: episode({ series: "Vikings: Valhalla", imdb_id: "tt11311302", tmdb_id: 110315 }, 2, 1),
    });
    expect(streams.data.map((stream) => stream.url)).toEqual(["https://voe.sx/vyswqokbf6bp"]);
  });

  test("an episode the season does not have is an empty list", async () => {
    const streams = await subject.streams.list({
      media: episode({ series: "Vikings: Valhalla", imdb_id: "tt11311302", tmdb_id: 110315 }, 2, 99),
    });
    expect(streams.data).toEqual([]);
  });

  test("a title not on the site is an empty list", async () => {
    const streams = await subject.streams.list({ media: movie({ title: "Unknown Show", tmdb_id: 1 }) });
    expect(streams.data).toEqual([]);
    const series = await subject.streams.list({ media: episode({ series: "Unknown Show" }, 1, 1) });
    expect(series.data).toEqual([]);
  });
});
