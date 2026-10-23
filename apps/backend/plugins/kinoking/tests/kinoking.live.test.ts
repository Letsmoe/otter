import { describe, expect, test } from "bun:test";
import { episode, movie, testPlugin } from "@neoworks-dev/otter-sdk/testing";
import plugin from "../plugin.ts";

// Hits the real site; expected to fail where the ISP DNS blocks it.
const subject = testPlugin(plugin);

describe("kinoking (live)", () => {
  test("Breaking Bad S1E1 has hoster links", async () => {
    const streams = await subject.streams.list({ media: episode({ series: "Breaking Bad", tmdb_id: 1396 }, 1, 1) });
    expect(streams.data.length).toBeGreaterThan(0);
  });

  test("The Matrix is found through the search", async () => {
    const streams = await subject.streams.list({ media: movie({ title: "The Matrix", year: 1999, tmdb_id: 603 }) });
    expect(streams.data.length).toBeGreaterThan(0);
  });
});
