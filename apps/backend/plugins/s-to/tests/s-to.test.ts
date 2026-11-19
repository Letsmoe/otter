import { describe, expect, test } from "bun:test";
import { episode, fixtures, movie, testPlugin } from "@letsmoe/otter-sdk/testing";
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

  test("a play link that stays behind the captcha is stored as is", async () => {
    const streams = await subject.streams.list({ media: episode({ series: "Dark", tmdb_id: 70523 }, 1, 1) });
    expect(streams.data.every((stream) => stream.url.startsWith("https://serienstream.to/r?t="))).toBe(true);
  });

  test("a play link is opened in the app and resolved by the hoster it leads to", async () => {
    const opened: string[] = [];
    const browser = {
      async open(url: string) {
        opened.push(url);
        return "https://unknown-hoster.example/e/abc";
      },
    };
    const withApp = testPlugin(plugin, { fetch: fixtures(`${import.meta.dir}/fixtures`), browser });
    // The hoster isn't in the fixtures: reaching it shows the play link was opened and its target handed on.
    await expect(withApp.sources.retrieve({ url: "https://serienstream.to/r?t=abc123" })).rejects.toThrow();
    expect(opened).toEqual(["https://serienstream.to/r?t=abc123"]);
  });

  test("without the app a play link is blocked, not broken", async () => {
    await expect(subject.sources.retrieve({ url: "https://serienstream.to/r?t=abc123" })).rejects.toMatchObject({ code: "blocked" });
  });

  test("movies are an empty list (s.to is series-only)", async () => {
    const streams = await subject.streams.list({ media: movie({ title: "Dark", tmdb_id: 1 }) });
    expect(streams.data).toEqual([]);
  });
});
