import { describe, expect, test } from "bun:test";
import { episode, movie, testPlugin } from "@neoworks-dev/otter-sdk/testing";
import plugin from "../plugin.ts";

const subject = testPlugin(plugin);
const LIVE_TIMEOUT_MS = 90_000;

describe("megakino (live)", () => {
  test(
    "finds hoster links for The Matrix",
    async () => {
      const streams = await subject.streams.list({ media: movie({ title: "The Matrix", year: 1999, tmdb_id: 603 }) });
      expect(streams.data.length).toBeGreaterThan(0);
    },
    LIVE_TIMEOUT_MS,
  );

  test(
    "finds hoster links for Breaking Bad S01E01",
    async () => {
      const streams = await subject.streams.list({
        media: episode({ series: "Breaking Bad", original_title: "Breaking Bad", tmdb_id: 1396 }, 1, 1),
      });
      expect(streams.data.length).toBeGreaterThan(0);
    },
    LIVE_TIMEOUT_MS,
  );
});
