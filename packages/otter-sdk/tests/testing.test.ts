import { describe, expect, test } from "bun:test";
import { mkdtempSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { definePlugin } from "../src/plugin.ts";
import { episode, fixtureName, fixtures, movie, testPlugin } from "../src/testing.ts";

describe("builders", () => {
  test("movie", () => {
    expect(movie({ title: "Matrix", year: 1999, tmdb_id: 603 })).toEqual({
      object: "movie",
      id: "movie:603",
      title: "Matrix",
      year: 1999,
      tmdb_id: 603,
    });
  });

  test("episode", () => {
    expect(episode({ series: "Dark", tmdb_id: 70523, year: 2017 }, 1, 2)).toEqual({
      object: "episode",
      id: "episode:70523:1:2",
      series: { object: "series", id: "series:70523", title: "Dark", tmdb_id: 70523, year: 2017 },
      season_number: 1,
      episode_number: 2,
    });
  });
});

describe("fixtures", () => {
  const directory = mkdtempSync(join(tmpdir(), "otter-fixtures-"));
  writeFileSync(join(directory, "s.to_serie_dark.html"), "<h1>Dark</h1>");
  writeFileSync(join(directory, "s.to_search_q_dark.json"), '{"hits":1}');
  writeFileSync(join(directory, "post.s.to_api_login"), "logged in");
  const serve = fixtures(directory);

  test("names fixtures after host, path and query", () => {
    expect(fixtureName("https://s.to/serie/dark?x=1&y=2", true)).toBe("s.to_serie_dark_x_1_y_2");
    expect(fixtureName("https://s.to/serie/dark?x=1", false)).toBe("s.to_serie_dark");
  });

  test("serves files with or without extension, query or not", async () => {
    expect(await (await serve("https://s.to/serie/dark?utm=1")).text()).toBe("<h1>Dark</h1>");
    const json = await serve("https://s.to/search?q=dark");
    expect(json.headers.get("content-type")).toBe("application/json");
    expect(await json.json()).toEqual({ hits: 1 });
    expect(await (await serve("https://s.to/api/login", { method: "POST" })).text()).toBe("logged in");
  });

  test("answers 404 for unknown urls", async () => {
    expect((await serve("https://s.to/missing")).status).toBe(404);
  });
});

describe("testPlugin", () => {
  const plugin = definePlugin({
    id: "demo",
    name: "Demo",
    version: "1",
    base_url: "https://demo.example",
    streams: {
      list: async (params, ctx) => {
        const page = await ctx.http.get("/watch");
        return ctx.list([{ url: (await page.text()).trim() }, { url: "https://x.example/y", label: "Mine" }]);
      },
    },
    sources: { domains: ["demo.example"], retrieve: async (params) => ({ url: params.url, mime_type: "video/mp4" }) },
  });

  test("runs handlers with a real context and completes the objects", async () => {
    const directory = mkdtempSync(join(tmpdir(), "otter-fixtures-"));
    writeFileSync(join(directory, "demo.example_watch.html"), "https://voe.sx/e/abc\n");
    const subject = testPlugin(plugin, { fetch: fixtures(directory) });

    const streams = await subject.streams.list({ media: movie({ title: "Matrix" }) });
    expect(streams).toEqual({
      object: "list",
      data: [
        { object: "stream", url: "https://voe.sx/e/abc", label: "VOE" },
        { object: "stream", url: "https://x.example/y", label: "Mine" },
      ],
    });

    expect(await subject.sources.retrieve({ url: "https://demo.example/v.mp4" })).toEqual({
      object: "source",
      url: "https://demo.example/v.mp4",
      mime_type: "video/mp4",
    });
  });

  test("labels unknown hosters by their domain", async () => {
    const subject = testPlugin(
      definePlugin({ id: "d", name: "D", version: "1", streams: { list: async (_, ctx) => ctx.list([{ url: "https://www.unknown.example/x" }]) } }),
    );
    expect((await subject.streams.list({ media: movie({ title: "A" }) })).data[0].label).toBe("unknown.example");
  });

  test("handlers missing from the definition fail clearly", async () => {
    const subject = testPlugin(definePlugin({ id: "d", name: "D", version: "1" }));
    await expect(subject.streams.list({ media: movie({ title: "A" }) })).rejects.toThrow("streams not implemented");
  });
});
