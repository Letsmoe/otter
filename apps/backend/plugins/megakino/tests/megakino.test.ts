import { describe, expect, test } from "bun:test";
import { episode, fixtures, movie, testPlugin } from "@neoworks-dev/otter-sdk/testing";
import plugin from "../plugin.ts";

const SESSION_TOKEN = "test-session";
const SEARCH_STORY_PREFIX = "story=breaking";

/**
 * Serves the fixtures like the site: the handshake sets the session cookie, and pages
 * are a JS stub without it. Searches for "breaking" use their own saved result page,
 * because fixtures cannot tell POST bodies apart.
 */
function siteFetch(directory: string): typeof fetch {
  const serve = fixtures(directory);
  return (async (input: RequestInfo | URL, init?: RequestInit) => {
    const url = String(input);
    if (url.includes("yg=token")) {
      return new Response(null, { status: 204, headers: { "set-cookie": `yg_token=${SESSION_TOKEN}; Max-Age=900; path=/` } });
    }
    if (!hasSessionCookie(init)) return new Response("<script>document.cookie='yg_token=1';</script>", { status: 200 });
    if (String(init?.body || "").toLowerCase().includes(SEARCH_STORY_PREFIX)) return serve("https://megakino23.com/index.php?do=search_breaking", init);
    return serve(input, init);
  }) as typeof fetch;
}

function hasSessionCookie(init?: RequestInit): boolean {
  const headers = (init?.headers || {}) as Record<string, string>;
  return headers.Cookie === `yg_token=${SESSION_TOKEN}`;
}

const subject = testPlugin(plugin, { fetch: siteFetch(`${import.meta.dir}/fixtures`) });

describe("megakino (offline)", () => {
  test("a movie is found by its TMDB id in the page images", async () => {
    const streams = await subject.streams.list({ media: movie({ title: "The Matrix", year: 1999, tmdb_id: 603 }) });
    expect(streams.data.map((stream) => stream.url)).toEqual(["https://voe.sx/e/agpq76ggcawg"]);
    expect(streams.data[0]).toMatchObject({ language: "de" });
  });

  test("an episode is found on its season page", async () => {
    const streams = await subject.streams.list({
      media: episode({ series: "Breaking Bad", original_title: "Breaking Bad", tmdb_id: 1396 }, 1, 1),
    });
    expect(streams.data.map((stream) => stream.url)).toEqual(["https://voe.sx/e/yvcyfo1zfcev"]);
  });

  test("a season of another section is found too", async () => {
    const streams = await subject.streams.list({
      media: episode({ series: "Breaking Bad", tmdb_id: 1396 }, 2, 1),
    });
    expect(streams.data.length).toBe(1);
    expect(streams.data[0].url).toStartWith("https://voe.sx/e/");
  });

  test("an episode the season does not have is an empty list", async () => {
    const streams = await subject.streams.list({ media: episode({ series: "Breaking Bad", tmdb_id: 1396 }, 1, 99) });
    expect(streams.data).toEqual([]);
  });

  test("a title not on the site is an empty list", async () => {
    const streams = await subject.streams.list({ media: movie({ title: "Unknown Show", tmdb_id: 1 }) });
    expect(streams.data).toEqual([]);
  });
});
