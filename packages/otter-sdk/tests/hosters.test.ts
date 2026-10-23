import { describe, expect, test } from "bun:test";
import { hosters } from "../src/hosters/index.ts";
import { HosterRegistry, defineHoster } from "../src/hosters/registry.ts";
import { assembleLink } from "../src/hosters/streamtape.ts";
import { PluginDeadLinkError, PluginSourceError } from "../src/errors.ts";
import { definePlugin } from "../src/plugin.ts";
import { createContext } from "../src/context.ts";
import { base64 } from "../src/extract.ts";
import { createCipheriv, randomBytes } from "node:crypto";

type Page = string | { status?: number; body?: string; headers?: Record<string, string> };

/** Serves pages by exact URL; records every request URL. */
function site(pages: Record<string, Page>) {
  const requested: string[] = [];
  const fetchStub = (async (input: RequestInfo | URL) => {
    const url = String(input);
    requested.push(url);
    const page = pages[url];
    if (page === undefined) return new Response("not found", { status: 404 });
    if (typeof page === "string") return new Response(page);
    return new Response(page.body || "", { status: page.status || 200, headers: page.headers });
  }) as typeof fetch;
  const ctx = createContext(definePlugin({ id: "t", name: "T", version: "1" }), { fetch: fetchStub, env: {} });
  return { ctx, requested };
}

function rot13(text: string): string {
  return text.replace(/[A-Za-z]/g, (letter) => {
    const base = letter <= "Z" ? 65 : 97;
    return String.fromCharCode(((letter.charCodeAt(0) - base + 13) % 26) + base);
  });
}

function shiftUp(text: string): string {
  return Array.from(text, (letter) => String.fromCharCode(letter.charCodeAt(0) + 3)).join("");
}

const toBase64 = (text: string) => Buffer.from(text).toString("base64");
const reverse = (text: string) => Array.from(text).reverse().join("");

/** What VOE's current pages hold: rot13(markers inserted into base64(shift(reverse(base64(json))))). */
function voePayload(sources: object, markers: string[]): string {
  const inner = toBase64(shiftUp(reverse(toBase64(JSON.stringify(sources)))));
  const marked = inner.replace(/(.{7})/g, `$1${markers[0]}`).replace(/(.{11})/g, `$1${markers[1]}`);
  return rot13(marked);
}

describe("registry", () => {
  const registry = new HosterRegistry([
    defineHoster({ name: "Alpha", domains: ["alpha.example"], retrieve: async () => ({ url: "a", mime_type: "video/mp4" }) }),
  ]);

  test("finds hosters by domain, ignoring www and matching subdomains", () => {
    expect(registry.supports("https://www.alpha.example/e/1")).toBe(true);
    expect(registry.supports("https://cdn.alpha.example/e/1")).toBe(true);
    expect(registry.supports("https://notalpha.example/e/1")).toBe(false);
    expect(registry.supports("garbage")).toBe(false);
    expect(registry.nameOf("https://alpha.example/e/1")).toBe("Alpha");
    expect(registry.nameOf("https://other.example/")).toBeUndefined();
    expect(registry.domains()).toEqual(["alpha.example"]);
  });

  test("rejects links no hoster serves when there is no fallback", async () => {
    const { ctx } = site({});
    await expect(registry.retrieve("https://other.example/x", ctx)).rejects.toThrow("no hoster serves");
  });

  test("follows a site's redirect link to the hoster it points at", async () => {
    const redirectingFetch = (async () => {
      const response = new Response("hoster page");
      Object.defineProperty(response, "url", { value: "https://alpha.example/e/1" });
      return response;
    }) as unknown as typeof fetch;
    const ctx = createContext(definePlugin({ id: "t", name: "T", version: "1" }), { fetch: redirectingFetch, env: {} });

    const source = await registry.retrieve("https://site.example/redirect/42", ctx);

    expect(source.url).toBe("a");
  });

  test("the built-in registry knows the shipped hosters", () => {
    for (const url of [
      "https://voe.sx/e/abc",
      "https://vidara.to/e/abc",
      "https://dood.to/e/abc",
      "https://filemoon.sx/e/abc",
      "https://streamtape.com/e/abc",
      "https://vinovo.to/e/abc",
    ]) {
      expect(hosters.supports(url)).toBe(true);
    }
    expect(hosters.nameOf("https://dood.to/e/abc")).toBe("Doodstream");
    expect(hosters.domains().length).toBeGreaterThan(100);
  });
});

describe("generic hoster", () => {
  test("finds media in the page, preferring HLS", async () => {
    const { ctx } = site({
      "https://unknown.example/v/1": '<script>var a="https://cdn.example/a.mp4"; var b="https://cdn.example/b.m3u8"</script>',
    });
    const source = await hosters.retrieve("https://unknown.example/v/1", ctx);
    expect(source).toEqual({ url: "https://cdn.example/b.m3u8", mime_type: "application/x-mpegURL" });
  });

  test("looks into iframes", async () => {
    const { ctx } = site({
      "https://unknown.example/v/1": '<iframe src="/player/1"></iframe>',
      "https://unknown.example/player/1": '<video src="https://cdn.example/movie.mp4"></video>',
    });
    const source = await hosters.retrieve("https://unknown.example/v/1", ctx);
    expect(source.url).toBe("https://cdn.example/movie.mp4");
  });

  test("fails with extraction_failed", async () => {
    const { ctx } = site({ "https://unknown.example/v/1": "<p>nothing</p>" });
    const failure = await hosters.retrieve("https://unknown.example/v/1", ctx).catch((error) => error);
    expect(failure).toBeInstanceOf(PluginSourceError);
    expect(failure.code).toBe("extraction_failed");
  });
});

describe("voe", () => {
  const sources = { source: "https://delivery.example/hls/master.m3u8?t=1", direct_access_url: "https://delivery.example/v.mp4" };

  test("decodes the json script using the markers of its companion script", async () => {
    const markers = ["@$", "^^"];
    const page =
      `<html><script type="application/json">["${voePayload(sources, markers)}"]</script>` + `<script src="/assets/app.js"></script></html>`;
    const { ctx } = site({
      "https://voe.sx/e/abc": page,
      "https://voe.sx/assets/app.js": `var lut=['@$','^^'];`,
    });
    const source = await hosters.retrieve("https://voe.sx/e/abc", ctx);
    expect(source).toEqual({ url: sources.direct_access_url, mime_type: "video/mp4", headers: { Referer: "https://voe.sx/" } });
  });

  test("follows the redirect page to the mirror", async () => {
    const redirect = `<script>if (permanentToken) { window.location.href='https://voe.sx/x?permanentToken=1' } else { window.location.href = 'https://mirror.example/e/abc'; }</script>`;
    const { ctx, requested } = site({
      "https://voe.sx/e/abc": redirect,
      "https://mirror.example/e/abc": `<script>var player = {"hls": "https://cdn.example/index.m3u8"}</script>`,
    });
    const source = await hosters.retrieve("https://voe.sx/e/abc", ctx);
    expect(requested).toEqual(["https://voe.sx/e/abc", "https://mirror.example/e/abc"]);
    expect(source).toMatchObject({ url: "https://cdn.example/index.m3u8", mime_type: "application/x-mpegURL" });
    expect(source.headers).toEqual({ Referer: "https://mirror.example/" });
  });

  test("decodes the older MKGMa variable", async () => {
    const encoded = rot13(toBase64(shiftUp(reverse(toBase64(JSON.stringify({ hls: "https://cdn.example/a.m3u8" }))))));
    const { ctx } = site({ "https://voe.sx/e/abc": `<script>var MKGMa="${encoded}";</script>` });
    expect((await hosters.retrieve("https://voe.sx/e/abc", ctx)).url).toBe("https://cdn.example/a.m3u8");
  });

  test("fails with extraction_failed when the page holds no stream", async () => {
    const { ctx } = site({ "https://voe.sx/e/abc": "<p>deleted</p>" });
    const failure = await hosters.retrieve("https://voe.sx/e/abc", ctx).catch((error) => error);
    expect(failure.code).toBe("extraction_failed");
  });
});

describe("vidara", () => {
  test("posts the file code to the stream api", async () => {
    const { ctx } = site({});
    const calls: Array<{ url: string; body: string }> = [];
    const fetchStub = (async (input: RequestInfo | URL, init?: RequestInit) => {
      calls.push({ url: String(input), body: String(init?.body) });
      return new Response(JSON.stringify({ streaming_url: "https://cdn.example/master.m3u8" }));
    }) as typeof fetch;
    const withApi = createContext(definePlugin({ id: "t", name: "T", version: "1" }), { fetch: fetchStub, env: {} });
    const source = await hosters.retrieve("https://vidara.to/e/FILE1", withApi);
    expect(calls).toEqual([{ url: "https://vidara.to/api/stream", body: '{"filecode":"FILE1","device":"web"}' }]);
    expect(source).toEqual({
      url: "https://cdn.example/master.m3u8",
      mime_type: "application/x-mpegURL",
      headers: { Referer: "https://vidara.to/e/FILE1", Origin: "https://vidara.to" },
    });
    expect(ctx).toBeDefined();
  });
});

describe("dood", () => {
  const playerPage =
    `<script>dsplayer.hotkeys = {}; $.get('/pass_md5/abc/def', function(data){ makePlay(data) });` +
    ` function makePlay(){ return a + "?token=tok123&expiry=" + Date.now(); }</script>`;

  test("builds the url from pass_md5 and the token", async () => {
    const { ctx, requested } = site({
      "https://dood.to/d/ID1": "<p>landing</p>",
      "https://dood.to/e/ID1": playerPage,
      "https://dood.to/pass_md5/abc/def": "https://storage.example/video/",
    });
    const source = await hosters.retrieve("https://dood.to/e/ID1", ctx);
    expect(requested).toEqual(["https://dood.to/d/ID1", "https://dood.to/e/ID1", "https://dood.to/pass_md5/abc/def"]);
    expect(source.url).toMatch(/^https:\/\/storage\.example\/video\/[A-Za-z0-9]{10}\?token=tok123&expiry=\d{13}$/);
    expect(source).toMatchObject({ mime_type: "video/mp4", headers: { Referer: "https://dood.to/" } });
  });

  test("follows the landing page's iframe", async () => {
    const { ctx } = site({
      "https://dood.to/d/ID1": '<iframe src="/e/ID1"></iframe>',
      "https://dood.to/e/ID1": playerPage,
      "https://dood.to/pass_md5/abc/def": "https://x.cloudflarestorage.com/v.mp4?sig=1",
    });
    expect((await hosters.retrieve("https://dood.to/d/ID1", ctx)).url).toBe("https://x.cloudflarestorage.com/v.mp4?sig=1");
  });
});

describe("streamtape", () => {
  test("assembles the link from its pieces", () => {
    const assignment = `"//streamtape.com/get_video?id=a&token=" + ('xcdFAKEtoken123').substring(3).substring(1)`;
    expect(assembleLink(assignment)).toBe("//streamtape.com/get_video?id=a&token=AKEtoken123".replace("AKEtoken123", "xcdFAKEtoken123".slice(4)));
    expect(assembleLink(`'//h/p?x=' + ('zzabc').substring(2)`)).toBe("//h/p?x=abc");
  });

  test("follows the get_video redirect without downloading the video", async () => {
    const page =
      `<script>document.getElementById('norobotlink').innerHTML = '//other/ignored';` +
      `document.getElementById('robotlink').innerHTML = '//streamtape.com/get_video?id=a&token=' + ('xcdXYZ').substring(3);</script>`;
    const { ctx, requested } = site({
      "https://streamtape.com/e/VID": page,
      "https://streamtape.com/get_video?id=a&token=XYZ&stream=1": {
        status: 302,
        headers: { location: "https://tapecontent.example/video.mp4" },
      },
    });
    const source = await hosters.retrieve("https://streamtape.com/v/VID", ctx);
    expect(requested).toEqual(["https://streamtape.com/e/VID", "https://streamtape.com/get_video?id=a&token=XYZ&stream=1"]);
    expect(source).toEqual({ url: "https://tapecontent.example/video.mp4", mime_type: "video/mp4", headers: { Referer: "https://streamtape.com/" } });
  });
});

const toBase64Url = (bytes: Buffer) => bytes.toString("base64url");

/** What filemoon's video API serves: AES-256-GCM, the key being key parts `version` and 31 - `version` (1-based). */
function filemoonPlayback(sources: object[], version: number) {
  const keyParts = Array.from({ length: 30 }, () => randomBytes(24));
  keyParts[version - 1] = randomBytes(16);
  keyParts[31 - version - 1] = randomBytes(16);
  const key = Buffer.concat([keyParts[version - 1], keyParts[31 - version - 1]]);
  const nonce = randomBytes(12);
  const cipher = createCipheriv("aes-256-gcm", key, nonce);
  const body = Buffer.concat([cipher.update(JSON.stringify({ sources })), cipher.final(), cipher.getAuthTag()]);
  return {
    algorithm: "AES-256-GCM",
    iv: toBase64Url(nonce),
    payload: toBase64Url(body),
    key_parts: keyParts.map(toBase64Url),
    version: String(version),
  };
}

describe("filemoon", () => {
  test("decrypts the video API playback and picks the tallest source", async () => {
    const playback = filemoonPlayback(
      [
        { label: "480p", height: 480, url: "https://cdn.example/480/master.m3u8?t=1" },
        { label: "720p", height: 720, url: "https://cdn.example/720/master.m3u8?t=1" },
      ],
      13,
    );
    const { ctx } = site({ "https://filemoon.to/api/videos/ID": JSON.stringify({ code: "ID", playback }) });
    const source = await hosters.retrieve("https://filemoon.to/e/ID", ctx);
    expect(source).toEqual({
      url: "https://cdn.example/720/master.m3u8?t=1",
      mime_type: "application/x-mpegURL",
      headers: { Referer: "https://filemoon.to/" },
    });
  });

  test("an API answer without playback falls back to the embed page", async () => {
    const { ctx } = site({
      "https://filemoon.sx/api/videos/ID": JSON.stringify({ error: "video not found" }),
      "https://filemoon.sx/e/ID": '<div data-hls-url="https://cdn.example/a.m3u8"></div>',
    });
    const source = await hosters.retrieve("https://filemoon.sx/e/ID", ctx);
    expect(source.url).toBe("https://cdn.example/a.m3u8");
  });

  test("reads data-hls-url", async () => {
    const { ctx } = site({
      "https://filemoon.sx/e/ID": '<div data-hls-url="https://cdn.example/a.m3u8?x=1&amp;y=2"></div>',
    });
    const source = await hosters.retrieve("https://filemoon.sx/e/ID", ctx);
    expect(source).toEqual({
      url: "https://cdn.example/a.m3u8?x=1&y=2",
      mime_type: "application/x-mpegURL",
      headers: { Referer: "https://filemoon.sx/" },
    });
  });

  test("follows the embed iframe", async () => {
    const { ctx } = site({
      "https://filemoon.sx/e/ID": '<iframe src="https://player.example/e/ID"></iframe>',
      "https://player.example/e/ID": '<script>player.setup({file:"https://cdn.example/b.m3u8"})</script>',
    });
    const source = await hosters.retrieve("https://filemoon.sx/e/ID", ctx);
    expect(source.url).toBe("https://cdn.example/b.m3u8");
    expect(source.headers).toEqual({ Referer: "https://player.example/" });
  });
});

describe("vinovo", () => {
  test("exchanges the page token for a stream url", async () => {
    const calls: Array<{ url: string; body: string }> = [];
    const fetchStub = (async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = String(input);
      if (init?.method === "POST") {
        calls.push({ url, body: String(init.body) });
        return new Response(JSON.stringify({ status: "ok", token: "TOK" }));
      }
      return new Response('<meta name="token" content="PAGE"><video data-base="https://stream.example"></video>');
    }) as typeof fetch;
    const ctx = createContext(definePlugin({ id: "t", name: "T", version: "1" }), { fetch: fetchStub, env: {} });
    const source = await hosters.retrieve("https://vinovo.to/d/ID9", ctx);
    expect(calls).toEqual([{ url: "https://vinovo.to/api/file/url/ID9", body: "token=PAGE&recaptcha=" }]);
    expect(source).toEqual({ url: "https://stream.example/stream/TOK", mime_type: "video/mp4", headers: { Referer: "https://vinovo.to/" } });
  });
});

describe("dead links", () => {
  const challengePage = "<html><title>Just a moment...</title>cloudflare challenges.cloudflare.com</html>";
  const doodMissingPlayer = "<html><head><title>Video not found | DoodStream</title></head><h1>Not Found</h1></html>";
  const vinovoMissingPage = '<meta name="token" content="T"><div class="container error"><h1>Not found</h1><p>Video not found</p></div>';

  async function failureOf(url: string, pages: Record<string, Page>) {
    const { ctx } = site(pages);
    return hosters.retrieve(url, ctx).catch((error) => error);
  }

  function expectDead(failure: any) {
    expect(failure).toBeInstanceOf(PluginDeadLinkError);
    expect(failure.code).toBe("dead_link");
  }

  function expectNotDead(failure: any) {
    expect(failure).toBeInstanceOf(Error);
    expect(failure).not.toBeInstanceOf(PluginDeadLinkError);
    expect(failure.code).not.toBe("dead_link");
  }

  test("a 404 or 410 on the page of the link is dead for every page-based hoster", async () => {
    for (const url of ["https://voe.sx/e/abc", "https://streamtape.com/v/abc", "https://vinovo.to/e/abc", "https://unknown.example/v/abc"]) {
      const playerUrl = url.replace("/v/", "/e/");
      expectDead(await failureOf(url, { [playerUrl]: { status: 404 }, [url]: { status: 404 } }));
      expectDead(await failureOf(url, { [playerUrl]: { status: 410 }, [url]: { status: 410 } }));
    }
  });

  test("a redirect page's mirror that answers 404 is dead for voe", async () => {
    const redirect = `<script>if (permanentToken) { window.location.href='x' } else { window.location.href = 'https://mirror.example/e/abc'; }</script>`;
    expectDead(await failureOf("https://voe.sx/e/abc", { "https://voe.sx/e/abc": redirect }));
  });

  test("dood: the player page titled Video not found is dead, also from /w/ links answering 404", async () => {
    expectDead(
      await failureOf("https://dood.to/e/ID1", {
        "https://dood.to/d/ID1": "<p>landing</p>",
        "https://dood.to/e/ID1": doodMissingPlayer,
      }),
    );
    expectDead(await failureOf("https://dood.yt/w/ID2", { "https://dood.yt/w/ID2": { status: 404, body: "File not found" } }));
    expectDead(
      await failureOf("https://dood.to/e/ID3", {
        "https://dood.to/d/ID3": '<iframe src="/e/"></iframe>',
        "https://dood.to/e/": "<title>File not found | DoodStream</title>",
      }),
    );
  });

  test("vinovo: the player page with an error box is dead", async () => {
    expectDead(await failureOf("https://vinovo.to/e/abc", { "https://vinovo.to/e/abc": vinovoMissingPage }));
  });

  test("vidara: the stream api answering 404 Video not found is dead", async () => {
    const failure = await failureOf("https://vidara.to/e/abc", {
      "https://vidara.to/api/stream": { status: 404, body: '{"error":"Video not found"}' },
    });
    expectDead(failure);
  });

  test("filemoon: the video api answering 404 with a missing video is dead, with or without a following embed page", async () => {
    const missing = { status: 404, body: '{"error":"video record missing: video not found"}' };
    expectDead(await failureOf("https://filemoon.to/e/abc", { "https://filemoon.to/api/videos/abc": missing }));
    expectDead(await failureOf("https://filemoon.sx/e/abc", { "https://filemoon.sx/api/videos/abc": { status: 404, body: "<html>no</html>" }, "https://filemoon.sx/e/abc": { status: 404 } }));
  });

  test("filemoon: a 404 api page without a JSON error is not enough, nor is an empty embed page", async () => {
    const failure = await failureOf("https://filemoon.sx/e/abc", {
      "https://filemoon.sx/api/videos/abc": { status: 404, body: "<html>spa</html>" },
      "https://filemoon.sx/e/abc": "<html>spa shell</html>",
    });
    expectNotDead(failure);
    expect(failure.code).toBe("extraction_failed");
  });

  test("a secondary request answering 404 is not dead", async () => {
    const failure = await failureOf("https://streamtape.com/v/VID", {
      "https://streamtape.com/e/VID": `<script>document.getElementById('robotlink').innerHTML = '//streamtape.com/get_video?id=a&token=' + ('xcdXYZ').substring(3);</script>`,
      "https://streamtape.com/get_video?id=a&token=XYZ&stream=1": { status: 404 },
    });
    // The redirect request is allowed to fail on its own; a plain 404 there is the stream link, not the page.
    expectNotDead(failure);
  });

  test("a missing voe companion script or frame is not dead", async () => {
    const page = `<html><script type="application/json">["abc"]</script><script src="/assets/app.js"></script></html>`;
    expectNotDead(await failureOf("https://voe.sx/e/abc", { "https://voe.sx/e/abc": page }));
    const frameFailure = await failureOf("https://filemoon.sx/e/abc", {
      "https://filemoon.sx/api/videos/abc": { status: 500 },
      "https://filemoon.sx/e/abc": '<iframe src="https://player.example/e/abc"></iframe>',
      "https://player.example/e/abc": { status: 404 },
    });
    expectNotDead(frameFailure);
  });

  test("403, 429, 5xx and Cloudflare challenges are never dead", async () => {
    const urls = [
      "https://voe.sx/e/abc",
      "https://streamtape.com/v/abc",
      "https://vinovo.to/e/abc",
      "https://dood.to/e/abc",
      "https://unknown.example/v/abc",
      "https://filemoon.sx/e/abc",
      "https://vidara.to/e/abc",
    ];
    const answers: Array<Page> = [{ status: 403, body: "forbidden" }, { status: 429 }, { status: 500 }, { status: 503, body: challengePage }, { status: 403, body: challengePage }];
    for (const url of urls) {
      for (const answer of answers) {
        const pages: Record<string, Page> = {};
        for (const target of [url, url.replace("/v/", "/e/"), url.replace("/e/", "/d/"), url.replace("/e/", "/api/videos/"), "https://vidara.to/api/stream"]) {
          pages[target] = answer;
        }
        expectNotDead(await failureOf(url, pages));
      }
    }
  }, 60_000);
});

test("base64 helper used by hosters handles voe's alphabets", () => {
  expect(base64(toBase64("x?>"))).toBe("x?>");
});
