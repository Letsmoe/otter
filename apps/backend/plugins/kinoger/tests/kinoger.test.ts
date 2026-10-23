import { describe, expect, test } from "bun:test";
import { episode, fixtures, movie, testPlugin } from "@neoworks-dev/otter-sdk/testing";
import plugin from "../plugin.ts";
import { parsePlayerCalls } from "../src/streams.ts";

const FIXTURE_DIRECTORY = `${import.meta.dir}/fixtures`;
const offline = testPlugin(plugin, { fetch: fixtures(FIXTURE_DIRECTORY) });
const live = testPlugin(plugin);
const LIVE_TIMEOUT_MS = 60_000;

describe("parsePlayerCalls", () => {
  test("reads each player's season/episode grid and ignores jQuery and document", () => {
    const html = "<script>pw.show(123, [['https://voe.sx/a','https://voe.sx/b'],['https://voe.sx/c']]); jQuery.show(1,[['x']]);</script>";
    const players = parsePlayerCalls(html);
    expect([...players.keys()]).toEqual(["pw"]);
    expect(players.get("pw")?.seasons).toEqual([["https://voe.sx/a", "https://voe.sx/b"], ["https://voe.sx/c"]]);
  });
});

describe("streams (offline)", () => {
  test("a movie lists the first entry of each player", async () => {
    const streams = await offline.streams.list({ media: movie({ title: "Inception", year: 2010, tmdb_id: 27205 }) });
    expect(streams.data.map((stream) => stream.url)).toEqual(["https://voe.sx/abc1", "https://filemoon.example/f1"]);
    expect(streams.data[0].language).toBe("de");
  });

  test("an episode lists the season/episode cell of each player", async () => {
    const streams = await offline.streams.list({ media: episode({ series: "Dark", tmdb_id: 70523 }, 2, 1) });
    expect(streams.data.map((stream) => stream.url)).toEqual(["https://voe.sx/s2e1", "https://vidara.to/e/s2e1"]);
  });

  test("a title kinoger does not list yields an empty list", async () => {
    const streams = await offline.streams.list({ media: movie({ title: "Unbekannter Film", tmdb_id: 1 }) });
    expect(streams.data).toEqual([]);
  });
});

describe("sources (offline)", () => {
  test("kinoger.ru embeds are decoded as VOE player pages", async () => {
    const source = await offline.sources.retrieve({ url: "https://kinoger.ru/e/ovwe23w90kg8" });
    expect(source.url).toBe("https://cdn.example.test/hls/ovwe23w90kg8/master.m3u8");
  });

  test("kinoger.be players yield their HLS URL with the kinoger.be referer", async () => {
    const source = await offline.sources.retrieve({ url: "https://kinoger.be/e/abc" });
    expect(source.url).toBe("https://cdn.example.test/hls/abc/index.m3u8");
    expect(source.mime_type).toBe("application/x-mpegURL");
    expect(source.headers?.Referer).toBe("https://kinoger.be/");
  });
});

// Live: kinoger.com currently fails the TLS handshake from some networks and kinoger.be answers 403 to search; a failure here is a finding.
describe("kinoger (live)", () => {
  test(
    "search finds titles for a common query",
    async () => {
      const streams = await live.streams.list({ media: movie({ title: "Inception", tmdb_id: 27205 }) });
      expect(streams.data.length).toBeGreaterThan(0);
      for (const stream of streams.data) expect(stream.url).toStartWith("http");
    },
    LIVE_TIMEOUT_MS,
  );
});
