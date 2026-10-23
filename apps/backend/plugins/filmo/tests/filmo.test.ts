import { describe, expect, test } from "bun:test";
import { fixtures, movie, testPlugin } from "@neoworks-dev/otter-sdk/testing";
import plugin from "../plugin.ts";

const CSRF_TOKEN = "R1pLZVmslMuZR1WNU6cfbc0UwNeqPpiUM7Vrb27M";
const SESSION_COOKIE = "filmo-session=session-test";
const HOSTER_URL = "https://hoster.example.test/e/abc";

/**
 * Serves the fixtures like the site: the movie page sets the session cookies, and the
 * mint endpoint answers only to a request carrying them and the page's CSRF token.
 */
function siteFetch(directory: string): typeof fetch {
  const serve = fixtures(directory);
  return (async (input: RequestInfo | URL, init?: RequestInit) => {
    const url = String(input);
    if (url.endsWith("/filmo.to/n") || url === "https://filmo.to/n") return mint(serve, input, init);
    if (url.includes("/n/")) return new Response(null, { status: 302, headers: { location: HOSTER_URL } });
    const response = await serve(input, init);
    return withSessionCookies(response);
  }) as typeof fetch;
}

async function mint(serve: typeof fetch, input: RequestInfo | URL, init?: RequestInit): Promise<Response> {
  const headers = (init?.headers || {}) as Record<string, string>;
  const sessionOk = (headers.Cookie || "").includes(SESSION_COOKIE);
  const csrfOk = headers["X-CSRF-TOKEN"] === CSRF_TOKEN;
  if (!sessionOk || !csrfOk) return new Response("page expired", { status: 419 });
  return serve(input, init);
}

function withSessionCookies(response: Response): Response {
  const headers = new Headers(response.headers);
  headers.set("set-cookie", "XSRF-TOKEN=xsrf%2Btest; Path=/; SameSite=Lax");
  headers.append("set-cookie", `${SESSION_COOKIE}; Path=/; HttpOnly`);
  return new Response(response.body, { status: response.status, headers });
}

const subject = testPlugin(plugin, { fetch: siteFetch(`${import.meta.dir}/fixtures`) });

describe("filmo (offline)", () => {
  test("lists one stream per provider chip, with the language of its row", async () => {
    const streams = await subject.streams.list({ media: movie({ title: "The Matrix", year: 1999, tmdb_id: 603 }) });
    expect(streams.data.map((stream) => [stream.label, stream.language])).toEqual([
      ["VOE", "en"],
      ["Byse", "en"],
      ["VOE", "de"],
      ["Byse", "de"],
    ]);
    expect(streams.data[0].url).toBe("https://filmo.to/movies/matrix-1#chip=0");
  });

  test("retrieves the hoster link by minting the chip's token with the page session", async () => {
    const source = await subject.sources.retrieve({ url: "https://filmo.to/movies/matrix-1#chip=0" });
    expect(source).toMatchObject({ url: "https://cdn.example.test/master.m3u8", mime_type: "application/x-mpegURL" });
  });

  test("a movie not on the site is an empty list", async () => {
    const streams = await subject.streams.list({ media: movie({ title: "Unknown Show", tmdb_id: 1 }) });
    expect(streams.data).toEqual([]);
  });
});
