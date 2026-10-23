import { describe, expect, test } from "bun:test";
import { definePlugin } from "../src/plugin.ts";
import { metaOf, toStreamListParams } from "../src/runtime.ts";
import type { StreamsArgs } from "../src/types.ts";
import { movie, testPlugin } from "../src/testing.ts";

interface WireResponse {
  result?: any;
  error?: string;
  code?: string;
}

/** Runs a plugin entry file the way the backend does: JSON on stdin, JSON on stdout. */
async function invoke(entry: string, request: unknown, env: Record<string, string> = {}): Promise<WireResponse> {
  const process = Bun.spawn(["bun", entry], {
    stdin: new TextEncoder().encode(JSON.stringify(request)),
    stdout: "pipe",
    stderr: "pipe",
    env: { ...Bun.env, ...env },
  });
  const output = await new Response(process.stdout).text();
  await process.exited;
  return JSON.parse(output);
}

const SAMPLE = `${import.meta.dir}/fixtures/sample/index.ts`;
const LEGACY = `${import.meta.dir}/fixtures/v1-plugin.ts`;

describe("runPlugin with a v1 handlers object", () => {
  test("answers meta, streams and resolve unchanged", async () => {
    const meta = await invoke(LEGACY, { capability: "meta" });
    expect(meta.result.name).toBe("legacy");
    const streams = await invoke(LEGACY, { capability: "streams", args: { media_type: "movie", title: "T", tmdb_id: 7 } });
    expect(streams.result.downloads).toEqual([{ label: "T", url: "https://legacy.example/7" }]);
    const resolved = await invoke(LEGACY, { capability: "resolve", args: { url: "https://legacy.example/x" } });
    expect(resolved.result).toEqual({ url: "https://legacy.example/x/video.mp4", mime_type: "video/mp4" });
  });

  test("reports unsupported capabilities without a code", async () => {
    const response = await invoke(LEGACY, { capability: "scrape" });
    expect(response).toEqual({ error: "unsupported capability: scrape" });
  });
});

describe("runPlugin with a definePlugin definition", () => {
  test("maps the definition to meta", async () => {
    const response = await invoke(SAMPLE, { capability: "meta" });
    expect(response.result).toEqual({
      name: "sample",
      display_name: "Sample",
      version: "0.0.1",
      capabilities: ["streams", "resolve"],
      domains: ["*"],
      base_url: "http://sample.invalid",
      languages: ["de"],
      media: ["movie", "episode"],
    });
  });

  test("rejects a missing resolve url and unknown capabilities", async () => {
    expect((await invoke(SAMPLE, { capability: "resolve", args: {} })).error).toBe("args.url required for resolve");
    expect((await invoke(SAMPLE, { capability: "bogus" })).error).toBe("unsupported capability: bogus");
  });

  test("reports an OtterError with its code", async () => {
    const response = await invoke(SAMPLE, { capability: "resolve", args: { url: "http://x.example/gone" } });
    expect(response).toEqual({ error: "video was removed", code: "extraction_failed" });
  });

  test("reports a dead link with the dead_link code", async () => {
    const response = await invoke(SAMPLE, { capability: "resolve", args: { url: "http://x.example/deleted" } });
    expect(response).toEqual({ error: "video was deleted", code: "dead_link" });
  });

  test("answers a title nobody has with an empty download list", async () => {
    const server = Bun.serve({ port: 0, fetch: () => new Response("<html></html>") });
    try {
      const response = await invoke(
        SAMPLE,
        { capability: "streams", args: { media_type: "movie", title: "Nothing", tmdb_id: 1 } },
        { OTTER_BASE_URL: `http://localhost:${server.port}` },
      );
      expect(response.result).toEqual({ downloads: [] });
    } finally {
      server.stop(true);
    }
  });
});

describe("meta mapping", () => {
  test("only declares the capabilities that exist", () => {
    const streamsOnly = definePlugin({ id: "a", name: "A", version: "1", streams: { list: async (_, ctx) => ctx.list([]) } });
    expect(metaOf(streamsOnly).capabilities).toEqual(["streams"]);
    expect(metaOf(streamsOnly).domains).toBeUndefined();

    const resolveOnly = definePlugin({
      id: "b",
      name: "B",
      version: "1",
      sources: { domains: ["x.com", "y.com"], retrieve: async () => ({ url: "u", mime_type: "video/mp4" }) },
    });
    expect(metaOf(resolveOnly).capabilities).toEqual(["resolve"]);
    expect(metaOf(resolveOnly).domains).toEqual(["x.com", "y.com"]);
  });
});

describe("stream params", () => {
  const base: StreamsArgs = { media_type: "movie", title: "Matrix", original_title: "The Matrix", year: 1999, tmdb_id: 603, imdb_id: "tt0133093" };

  test("builds a movie ref", () => {
    expect(toStreamListParams(base).media).toEqual({
      object: "movie",
      id: "movie:603",
      title: "Matrix",
      original_title: "The Matrix",
      year: 1999,
      tmdb_id: 603,
      imdb_id: "tt0133093",
    });
  });

  test("builds an episode ref from the series fields", () => {
    const media = toStreamListParams({ ...base, media_type: "episode", tmdb_id: 70523, season_number: 2, episode_number: 5 }).media;
    expect(media).toMatchObject({
      object: "episode",
      id: "episode:70523:2:5",
      season_number: 2,
      episode_number: 5,
      series: { object: "series", id: "series:70523", title: "Matrix" },
    });
  });

  test("falls back to the IMDb id, then the title, when TMDB is unknown", () => {
    expect(toStreamListParams({ media_type: "movie", title: "X", imdb_id: "tt1" }).media.id).toBe("movie:tt1");
    expect(toStreamListParams({ media_type: "movie", title: "Die Brücke" }).media.id).toBe("movie:die-bruecke");
  });
});

describe("stream language", () => {
  const listing = async () => ({ object: "list" as const, data: [{ url: "https://voe.sx/e/1" }, { url: "https://voe.sx/e/2", language: "en-sub" }] });

  test("links without a language get the plugin's only language", async () => {
    const plugin = definePlugin({ id: "de-site", name: "DE", version: "1", languages: ["de"], streams: { list: listing } });
    const streams = await testPlugin(plugin).streams.list({ media: movie({ title: "Dark" }) });
    expect(streams.data.map((stream) => stream.language)).toEqual(["de", "en-sub"]);
  });

  test("sites with several languages leave unknown links open", async () => {
    const plugin = definePlugin({ id: "mixed", name: "Mixed", version: "1", languages: ["de", "en"], streams: { list: listing } });
    const streams = await testPlugin(plugin).streams.list({ media: movie({ title: "Dark" }) });
    expect(streams.data[0].language).toBeUndefined();
  });
});
