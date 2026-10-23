import { describe, expect, test } from "bun:test";
import { episode, testPlugin } from "@neoworks-dev/otter-sdk/testing";
import plugin from "../plugin.ts";

const subject = testPlugin(plugin);
const LIVE_TIMEOUT_MS = 90_000;

describe("aniworld (live)", () => {
  test(
    "finds stream links for Attack on Titan S01E01",
    async () => {
      const streams = await subject.streams.list({
        media: episode({ series: "Attack on Titan", original_title: "Shingeki no Kyojin", year: 2013, tmdb_id: 1429 }, 1, 1),
      });
      expect(streams.data.length).toBeGreaterThan(0);
      for (const stream of streams.data) expect(stream.url).toStartWith("https://aniworld.to/redirect/");
    },
    LIVE_TIMEOUT_MS,
  );
});
