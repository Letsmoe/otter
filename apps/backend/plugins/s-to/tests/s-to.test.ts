import { describe, expect, test } from "bun:test";
import { episode, fixtures, movie, testPlugin } from "@letsmoe/otter-sdk/testing";
import plugin from "../plugin.ts";

const subject = testPlugin(plugin, { fetch: fixtures(`${import.meta.dir}/fixtures`) });

describe("s-to (offline)", () => {
  test("lists the German and subtitled links of an episode", async () => {
    const streams = await subject.streams.list({ media: episode({ series: "Dark", tmdb_id: 70523 }, 1, 1) });
    expect(streams.data.map((stream) => stream.label)).toEqual(["VOE — Deutsch", "Vidoza — Untertitel"]);
    expect(streams.data.map((stream) => stream.language)).toEqual(["de", "de-sub"]);
    // The play links' tokens change with every page load: the episode page and button are stored.
    expect(streams.data.map((stream) => stream.url)).toEqual([
      "https://serienstream.to/serie/dark/staffel-1/episode-1#provider=VOE&language=1",
      "https://serienstream.to/serie/dark/staffel-1/episode-1#provider=Vidoza&language=3",
    ]);
  });

  test("matches titles with umlauts", async () => {
    const streams = await subject.streams.list({ media: episode({ series: "Ueber den Dingen" }, 1, 1) });
    expect(streams.data.map((stream) => stream.url)).toEqual([
      "https://serienstream.to/serie/ueber-den-dingen/staffel-1/episode-1#provider=Streamtape&language=1",
    ]);
  });

  test("a series not on the site is an empty list", async () => {
    const streams = await subject.streams.list({ media: episode({ series: "Unknown Show" }, 1, 1) });
    expect(streams.data).toEqual([]);
  });

  test("an episode the site does not have is an empty list", async () => {
    const streams = await subject.streams.list({ media: episode({ series: "Dark" }, 9, 9) });
    expect(streams.data).toEqual([]);
  });

  test("a link opens its episode page in the app, clicks its button and posts the captcha form", async () => {
    const opened: Array<{ url: string; options: unknown }> = [];
    const browser = {
      async open(url: string, options?: unknown) {
        opened.push({ url, options });
        return "https://unknown-hoster.example/e/abc";
      },
    };
    const withApp = testPlugin(plugin, { fetch: fixtures(`${import.meta.dir}/fixtures`), browser });
    // The hoster isn't in the fixtures: reaching it shows the link was opened and its target handed on.
    const link = "https://serienstream.to/serie/dark/staffel-1/episode-1#provider=VOE&language=1";
    await expect(withApp.sources.retrieve({ url: link })).rejects.toThrow();
    expect(opened).toEqual([
      {
        url: "https://serienstream.to/serie/dark/staffel-1/episode-1",
        options: { click: 'button.link-box[data-provider-name="VOE"][data-language-id="1"]', submit: "#player-prepare-form" },
      },
    ]);
  });

  test("without the app a link is blocked, not broken", async () => {
    const link = "https://serienstream.to/serie/dark/staffel-1/episode-1#provider=VOE&language=1";
    await expect(subject.sources.retrieve({ url: link })).rejects.toMatchObject({ code: "blocked" });
  });

  test("an old play link is dead: its token only ever worked inside the page", async () => {
    await expect(subject.sources.retrieve({ url: "https://serienstream.to/r?t=abc123" })).rejects.toMatchObject({ code: "dead_link" });
  });

  test("movies are an empty list (s.to is series-only)", async () => {
    const streams = await subject.streams.list({ media: movie({ title: "Dark", tmdb_id: 1 }) });
    expect(streams.data).toEqual([]);
  });
});
