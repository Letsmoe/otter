import { describe, expect, test } from "bun:test";
import { movie, testPlugin } from "@neoworks-dev/otter-sdk/testing";
import plugin from "../plugin.ts";

// Hits the real site and API; expected to fail where the ISP DNS blocks them.
const subject = testPlugin(plugin);

describe("aurorascreen (live)", () => {
  test("The Matrix has a playable HLS stream", async () => {
    const streams = await subject.streams.list({ media: movie({ title: "The Matrix", year: 1999, tmdb_id: 603 }) });
    expect(streams.data.length).toBeGreaterThan(0);
    const source = await subject.sources.retrieve({ url: streams.data[0].url });
    expect(source.url).toStartWith("https://");
  });
});
