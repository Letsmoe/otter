import { describe, expect, test } from "bun:test";
import { episode, fixtures, movie, testPlugin } from "@neoworks-dev/otter-sdk/testing";
import plugin from "../plugin.ts";

const subject = testPlugin(plugin, { fetch: fixtures(`${import.meta.dir}/fixtures`) });

describe("kinoz (offline)", () => {
  test("lists one link per hoster of a movie page", async () => {
    const streams = await subject.streams.list({ media: movie({ title: "In Search of Darkness", year: 2019 }) });
    expect(streams.data.map((stream) => stream.label)).toEqual(["Vinovo.to", "Dood.to"]);
    expect(streams.data.map((stream) => stream.url)).toEqual([
      "https://kinoz.to/aGET/Mirror/In_Search_of_Darkness&Hoster=104",
      "https://kinoz.to/aGET/Mirror/In_Search_of_Darkness&Hoster=95",
    ]);
  });

  // kinoz has two "Bosch (2014)" entries: Bosch-1 only holds S7E4, Bosch holds seasons 1-4 and 7.
  test("episode links come from the entry that has the episode", async () => {
    const streams = await subject.streams.list({ media: episode({ series: "Bosch", year: 2014, tmdb_id: 60585 }, 3, 4) });
    expect(streams.data.map((stream) => stream.url)).toEqual(["https://kinoz.to/aGET/Mirror/Bosch&Hoster=95&Season=3&Episode=4"]);
    expect(streams.data.map((stream) => stream.label)).toEqual(["Dood.to"]);
  });

  test("an episode no entry lists has no links", async () => {
    const streams = await subject.streams.list({ media: episode({ series: "Bosch", year: 2014, tmdb_id: 60585 }, 5, 1) });
    expect(streams.data).toEqual([]);
  });

  test("a title that only partly matches a result is not matched", async () => {
    const streams = await subject.streams.list({ media: movie({ title: "dark" }) });
    expect(streams.data).toEqual([]);
  });
});
