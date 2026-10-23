import { describe, expect, test } from "bun:test";
import { movie, testPlugin } from "@neoworks-dev/otter-sdk/testing";
import plugin from "../plugin.ts";

// Hits the real site and player; expected to fail where the ISP DNS blocks them.
const subject = testPlugin(plugin);

describe("vidsrc (live)", () => {
  test("The Matrix resolves to a tokenized HLS playlist", async () => {
    const streams = await subject.streams.list({ media: movie({ title: "The Matrix", year: 1999, tmdb_id: 603 }) });
    expect(streams.data.length).toBe(1);
    const source = await subject.sources.retrieve({ url: streams.data[0].url });
    expect(source.url).toStartWith("https://");
    expect(source.url).toContain("token=");
    expect(source.mime_type).toBe("application/x-mpegURL");
  });
});
