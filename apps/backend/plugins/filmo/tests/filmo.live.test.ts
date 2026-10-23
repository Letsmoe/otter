import { describe, expect, test } from "bun:test";
import { movie, testPlugin } from "@neoworks-dev/otter-sdk/testing";
import plugin from "../plugin.ts";

const subject = testPlugin(plugin);
const LIVE_TIMEOUT_MS = 90_000;

describe("filmo (live)", () => {
  test(
    "finds provider chips for The Matrix",
    async () => {
      const streams = await subject.streams.list({ media: movie({ title: "The Matrix", year: 1999, tmdb_id: 603 }) });
      expect(streams.data.length).toBeGreaterThan(0);
    },
    LIVE_TIMEOUT_MS,
  );

  test(
    "retrieves a playable URL for the first chip of The Matrix",
    async () => {
      const streams = await subject.streams.list({ media: movie({ title: "The Matrix", year: 1999, tmdb_id: 603 }) });
      const source = await subject.sources.retrieve({ url: streams.data[0].url });
      expect(source.url).toStartWith("http");
    },
    LIVE_TIMEOUT_MS,
  );
});
