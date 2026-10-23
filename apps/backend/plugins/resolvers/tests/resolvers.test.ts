import { describe, expect, test } from "bun:test";
import { fixtures, testPlugin } from "@neoworks-dev/otter-sdk/testing";
import plugin from "../plugin.ts";

const FIXTURE_DIRECTORY = `${import.meta.dir}/fixtures`;
const offline = testPlugin(plugin, { fetch: fixtures(FIXTURE_DIRECTORY) });
const live = testPlugin(plugin);
const LIVE_TIMEOUT_MS = 60_000;

describe("declaration", () => {
  test("serves the hoster domains and every other host as fallback", () => {
    expect(plugin.sources?.domains).toContain("voe.sx");
    expect(plugin.sources?.domains).toContain("vidara.to");
    expect(plugin.sources?.fallback).toBe(true);
  });
});

describe("retrieve (offline)", () => {
  test("VOE redirect page is followed to the player and decoded", async () => {
    const source = await offline.sources.retrieve({ url: "https://voe.sx/ovwe23w90kg8" });
    expect(source.url).toBe("https://cdn.example.test/hls/ovwe23w90kg8/master.m3u8");
    expect(source.mime_type).toBe("application/x-mpegURL");
    expect(source.headers?.Referer).toBe("https://megankiddifference.com/");
  });

  test("Vidara posts the filecode and returns the streaming URL", async () => {
    const source = await offline.sources.retrieve({ url: "https://vidara.to/e/y6XzEuzyZ6QAR" });
    expect(source.url).toContain("master.m3u8");
    expect(source.headers?.Origin).toBe("https://vidara.to");
  });

  test("unknown hosts go through the generic page scanner", async () => {
    const source = await offline.sources.retrieve({ url: "https://hoster.example.test/e/abc" });
    expect(source.url).toBe("https://cdn.example.test/video/generic.mp4");
    expect(source.mime_type).toBe("video/mp4");
  });

  test("a page without a stream fails with extraction_failed", async () => {
    const attempt = offline.sources.retrieve({ url: "https://hoster.example.test/e/empty" });
    await expect(attempt).rejects.toMatchObject({ code: "extraction_failed" });
  });
});

describe("retrieve (live)", () => {
  test(
    "VOE link resolves to a playable URL",
    async () => {
      const source = await live.sources.retrieve({ url: "https://voe.sx/ovwe23w90kg8" });
      expect(source.url).toStartWith("http");
    },
    LIVE_TIMEOUT_MS,
  );

  test(
    "Vidara link resolves to a playable URL",
    async () => {
      const source = await live.sources.retrieve({ url: "https://vidara.to/e/y6XzEuzyZ6QAR" });
      expect(source.url).toStartWith("http");
    },
    LIVE_TIMEOUT_MS,
  );
});
