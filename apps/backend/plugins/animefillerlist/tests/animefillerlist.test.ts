import { describe, expect, test } from "bun:test";
import { fixtures, testPlugin } from "@letsmoe/otter-sdk/testing";
import type { SeriesRef } from "@letsmoe/otter-sdk";
import plugin from "../plugin.ts";
import { kindOf } from "../src/filler.ts";

const subject = testPlugin(plugin, { fetch: fixtures(`${import.meta.dir}/fixtures`) });

function series(title: string, originalTitle?: string): SeriesRef {
  return { object: "series", id: `series:${title}`, title, original_title: originalTitle, tmdb_id: 31910 };
}

describe("animefillerlist (offline)", () => {
  test("declares only the filler capability", () => {
    expect(plugin.filler).toBeDefined();
    expect(plugin.streams).toBeUndefined();
    expect(plugin.subtitles).toBeUndefined();
    expect(plugin.sources).toBeUndefined();
  });

  test("lists every episode of the matching show with its kind", async () => {
    const list = await subject.filler.list({ series: series("Naruto Shippuden") });
    expect(list.data).toEqual([
      { number: 1, kind: "mixed" },
      { number: 2, kind: "canon" },
      { number: 3, kind: "canon" },
      { number: 4, kind: "filler" },
      { number: 5, kind: "filler" },
    ]);
  });

  test("the original title finds the show too, and the shows with a longer name aren't taken", async () => {
    const list = await subject.filler.list({ series: series("Naruto: Shippuden (2007)", "Naruto Shippuden") });
    expect(list.data.length).toBe(5);
    const other = await subject.filler.list({ series: series("Bleach") });
    expect(other.data).toEqual([]);
  });

  test("a series the site doesn't list has no episodes", async () => {
    const list = await subject.filler.list({ series: series("Breaking Bad") });
    expect(list.data).toEqual([]);
  });

  test("the type labels map to kinds", () => {
    expect(kindOf("Manga Canon")).toBe("canon");
    expect(kindOf("Anime Canon")).toBe("canon");
    expect(kindOf("Mixed Canon/Filler")).toBe("mixed");
    expect(kindOf("Filler")).toBe("filler");
    expect(kindOf("")).toBeUndefined();
  });
});
