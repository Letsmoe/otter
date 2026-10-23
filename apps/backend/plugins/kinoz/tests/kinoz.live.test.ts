import { describe, expect, test } from "bun:test";
import { movie, testPlugin } from "@neoworks-dev/otter-sdk/testing";
import plugin from "../plugin.ts";

const subject = testPlugin(plugin);
const LIVE_TIMEOUT_MS = 90_000;

describe("kinoz (live)", () => {
  test(
    "finds hoster links for In Search of Darkness",
    async () => {
      const streams = await subject.streams.list({ media: movie({ title: "In Search of Darkness", year: 2019 }) });
      expect(streams.data.length).toBeGreaterThan(0);
      for (const stream of streams.data) expect(stream.url).toStartWith("https://kinoz.to/aGET/Mirror/");
    },
    LIVE_TIMEOUT_MS,
  );
});
