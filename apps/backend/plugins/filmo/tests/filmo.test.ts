import { describe, expect, test } from "bun:test";
import { fixtures, movie, testPlugin } from "@letsmoe/otter-sdk/testing";
import plugin from "../plugin.ts";

const CSRF_TOKEN = "R1pLZVmslMuZR1WNU6cfbc0UwNeqPpiUM7Vrb27M";
const SESSION_COOKIE = "filmo-session=session-test";
const HOSTER_URL = "https://hoster.example.test/e/abc";

// Some hosters' mint links answer with a page to click through instead of a redirect.
let mintAnswersWithPage = false;
const OPEN_PAGE = `<html><head><title>Video öffnen – Filmo</title></head>
<body><a class="open" href="${HOSTER_URL}">Video öffnen</a></body></html>`;

function mintedLink(): Response {
  if (mintAnswersWithPage) return new Response(OPEN_PAGE, { status: 200, headers: { "content-type": "text/html" } });
  return new Response(null, { status: 302, headers: { location: HOSTER_URL } });
}

/**
 * Serves the fixtures like the site: the movie page sets the session cookies, and the
 * mint endpoint answers only to a request carrying them and the page's CSRF token.
 */
function siteFetch(directory: string): typeof fetch {
  const serve = fixtures(directory);
  return (async (input: RequestInfo | URL, init?: RequestInit) => {
    const url = String(input);
    if (url.endsWith("/filmo.to/n") || url === "https://filmo.to/n") return mint(serve, input, init);
    if (url.includes("/n/")) return mintedLink();
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
  test("lists one stream per playable provider chip, with the language of its row", async () => {
    const streams = await subject.streams.list({ media: movie({ title: "The Matrix", year: 1999, tmdb_id: 603 }) });
    // Byse has no resolver; its chips are left out.
    expect(streams.data.map((stream) => [stream.label, stream.language])).toEqual([
      ["VOE", "en"],
      ["VOE", "de"],
    ]);
    expect(streams.data.map((stream) => stream.url)).toEqual([
      "https://filmo.to/movies/matrix-1#chip=0",
      "https://filmo.to/movies/matrix-1#chip=2",
    ]);
  });

  test("retrieves the hoster link by minting the chip's token with the page session", async () => {
    const source = await subject.sources.retrieve({ url: "https://filmo.to/movies/matrix-1#chip=0" });
    expect(source).toMatchObject({ url: "https://cdn.example.test/master.m3u8", mime_type: "application/x-mpegURL" });
  });

  test("follows a mint link that answers with an open page instead of a redirect", async () => {
    mintAnswersWithPage = true;
    try {
      const source = await subject.sources.retrieve({ url: "https://filmo.to/movies/matrix-1#chip=0" });
      expect(source).toMatchObject({ url: "https://cdn.example.test/master.m3u8" });
    } finally {
      mintAnswersWithPage = false;
    }
  });

  test("a movie not on the site is an empty list", async () => {
    const streams = await subject.streams.list({ media: movie({ title: "Unknown Show", tmdb_id: 1 }) });
    expect(streams.data).toEqual([]);
  });
});
