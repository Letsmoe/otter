import { describe, expect, test } from "bun:test";
import { episode, movie, testPlugin } from "@neoworks-dev/otter-sdk/testing";
import plugin from "../plugin.ts";

const subject = testPlugin(plugin);
const LIVE_TIMEOUT_MS = 90_000;

describe("movie2k (live)", () => {
  test(
    "finds hoster links for The Matrix",
    async () => {
      const streams = await subject.streams.list({
        media: movie({ title: "The Matrix", original_title: "The Matrix", year: 1999, tmdb_id: 603, imdb_id: "tt0133093" }),
      });
      expect(streams.data.length).toBeGreaterThan(0);
    },
    LIVE_TIMEOUT_MS,
  );

  test(
    "finds hoster links for Vikings: Valhalla S02E01",
    async () => {
      const streams = await subject.streams.list({
        media: episode({ series: "Vikings: Valhalla", imdb_id: "tt11311302", tmdb_id: 110315 }, 2, 1),
      });
      expect(streams.data.length).toBeGreaterThan(0);
    },
    LIVE_TIMEOUT_MS,
  );
});
