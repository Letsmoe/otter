import { describe, expect, test } from "bun:test";
import { definePlugin } from "../src/plugin.ts";
import { metaOf, toSubtitleListParams } from "../src/runtime.ts";
import type { SubtitlesArgs } from "../src/types.ts";
import { episode, movie, testPlugin } from "../src/testing.ts";
import plugin from "./fixtures/subtitles/plugin.ts";

const ENTRY = `${import.meta.dir}/fixtures/subtitles/index.ts`;

async function invoke(request: unknown): Promise<{ result?: any; error?: string }> {
  const process = Bun.spawn(["bun", ENTRY], {
    stdin: new TextEncoder().encode(JSON.stringify(request)),
    stdout: "pipe",
    stderr: "pipe",
  });
  const output = await new Response(process.stdout).text();
  await process.exited;
  return JSON.parse(output);
}

describe("subtitles capability", () => {
  test("is declared in meta", () => {
    expect(metaOf(plugin).capabilities).toEqual(["subtitles"]);
  });

  test("completes files with the object tag and the srt default", async () => {
    const list = await testPlugin(plugin).subtitles.list({ media: movie({ title: "Dark", tmdb_id: 1 }), languages: [] });
    expect(list.data.map((file) => [file.object, file.format])).toEqual([
      ["subtitle", "srt"],
      ["subtitle", "vtt"],
    ]);
  });

  test("passes the wanted languages to the plugin", async () => {
    const subject = testPlugin(plugin);
    const list = await subject.subtitles.list({ media: episode({ series: "Dark", tmdb_id: 70523 }, 1, 2), languages: ["en"] });
    expect(list.data.map((file) => file.language)).toEqual(["en"]);
  });

  test("a definition without subtitles refuses to list them", async () => {
    const streamsOnly = definePlugin({ id: "a", name: "A", version: "1", streams: { list: async (_, ctx) => ctx.list([]) } });
    expect(testPlugin(streamsOnly).subtitles.list({ media: movie({ title: "X" }), languages: [] })).rejects.toThrow(
      "subtitles not implemented",
    );
  });
});

describe("subtitle params", () => {
  const args: SubtitlesArgs = { media_type: "episode", title: "Dark", tmdb_id: 70523, season_number: 1, episode_number: 2, languages: ["de", "en"] };

  test("builds the media ref and keeps the languages", () => {
    const params = toSubtitleListParams(args);
    expect(params.media).toMatchObject({ object: "episode", id: "episode:70523:1:2" });
    expect(params.languages).toEqual(["de", "en"]);
  });

  test("no languages means any language", () => {
    expect(toSubtitleListParams({ media_type: "movie", title: "X" }).languages).toEqual([]);
  });
});

describe("runPlugin subtitles dispatch", () => {
  test("answers the wire format with snake_case fields", async () => {
    const response = await invoke({ capability: "subtitles", args: { media_type: "movie", title: "Dark", languages: ["de"] } });
    expect(response.result).toEqual({
      subtitles: [{ url: "https://subs.example/de.srt", language: "de", format: "srt", release: "Dark.S01E02" }],
    });
  });

  test("reports meta with the subtitles capability", async () => {
    const response = await invoke({ capability: "meta" });
    expect(response.result.capabilities).toEqual(["subtitles"]);
  });

  test("rejects subtitles for a plugin without them", async () => {
    const legacy = Bun.spawn(["bun", `${import.meta.dir}/fixtures/v1-plugin.ts`], {
      stdin: new TextEncoder().encode(JSON.stringify({ capability: "subtitles", args: {} })),
      stdout: "pipe",
    });
    const output = JSON.parse(await new Response(legacy.stdout).text());
    expect(output.error).toBe("subtitles not implemented");
  });
});
