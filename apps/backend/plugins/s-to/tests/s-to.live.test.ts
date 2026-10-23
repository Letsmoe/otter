import { describe, expect, test } from "bun:test";
import { episode, testPlugin } from "@neoworks-dev/otter-sdk/testing";
import plugin from "../plugin.ts";

const subject = testPlugin(plugin);
const LIVE_TIMEOUT_MS = 90_000;

describe("s-to (live)", () => {
  test(
    "finds stream links for Dark S01E01",
    async () => {
      const streams = await subject.streams.list({ media: episode({ series: "Dark", tmdb_id: 70523 }, 1, 1) });
      expect(streams.data.length).toBeGreaterThan(0);
      for (const stream of streams.data) expect(stream.url).toStartWith("http");
    },
    LIVE_TIMEOUT_MS,
  );
});
