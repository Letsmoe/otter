import { describe, expect, test } from "bun:test";
import { episode, fixtures, movie, testPlugin } from "@neoworks-dev/otter-sdk/testing";
import plugin from "../plugin.ts";

const subject = testPlugin(plugin, { fetch: fixtures(`${import.meta.dir}/fixtures`) });

describe("filmpalast (offline)", () => {
  test("lists the player links of a movie page", async () => {
    const streams = await subject.streams.list({ media: movie({ title: "Inception", year: 2010, tmdb_id: 27205 }) });
    expect(streams.data.length).toBeGreaterThan(0);
    expect(streams.data.map((stream) => stream.url)).toEqual([
      "https://firestream.to/e/777zhD-W",
      "https://odysseusa.cc/e/YgztaLbNXPhPr",
      "https://voe.sx/yhwifm7duchl",
      "https://vidsonic.net/e/8zwnbxa44ivc",
    ]);
    expect(streams.data[0].language).toBe("de");
  });

  test("lists the player links of an episode page", async () => {
    const streams = await subject.streams.list({ media: episode({ series: "Dark Matter", tmdb_id: 1 }, 1, 13) });
    expect(streams.data.length).toBeGreaterThan(0);
    const urls = streams.data.map((stream) => stream.url);
    expect(new Set(urls).size).toBe(urls.length);
    for (const url of urls) expect(url).toStartWith("http");
  });

  test("a title not on the site is an empty list", async () => {
    const streams = await subject.streams.list({ media: movie({ title: "Xyzzy" }) });
    expect(streams.data).toEqual([]);
  });
});
