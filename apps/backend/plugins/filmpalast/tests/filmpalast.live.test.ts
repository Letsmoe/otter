import { describe, expect, test } from "bun:test";
import { movie, testPlugin } from "@neoworks-dev/otter-sdk/testing";
import plugin from "../plugin.ts";

const subject = testPlugin(plugin);
const LIVE_TIMEOUT_MS = 90_000;

describe("filmpalast (live)", () => {
  test(
    "finds player links for Inception",
    async () => {
      const streams = await subject.streams.list({ media: movie({ title: "Inception", year: 2010, tmdb_id: 27205 }) });
      expect(streams.data.length).toBeGreaterThan(0);
      for (const stream of streams.data) expect(stream.url).toStartWith("http");
    },
    LIVE_TIMEOUT_MS,
  );
});
