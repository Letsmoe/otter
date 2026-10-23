import { describe, expect, test } from "bun:test";
import { movie, testPlugin } from "@neoworks-dev/otter-sdk/testing";
import plugin from "../plugin.ts";

// Hits the real site; expected to fail where the ISP DNS blocks it.
const subject = testPlugin(plugin);

describe("einschalten (live)", () => {
  test("The Matrix has a watch link", async () => {
    const streams = await subject.streams.list({ media: movie({ title: "Matrix", year: 1999, tmdb_id: 603 }) });
    expect(streams.data[0].url).toStartWith("https://");
  });
});
