import { describe, expect, test } from "bun:test";
import { createContext, effectiveBaseUrl } from "../src/context.ts";
import { PluginBlockedError, PluginRateLimitError, PluginSourceError, HttpError } from "../src/errors.ts";
import { acceptLanguage, createHttpClient } from "../src/http-client.ts";
import { definePlugin } from "../src/plugin.ts";

interface Seen {
  url: string;
  init: RequestInit;
}

/** A fetch that answers from a script of responses and records what it was asked. */
function scripted(responses: Array<Response | Error>): { fetch: typeof fetch; seen: Seen[] } {
  const seen: Seen[] = [];
  const fetchStub = (async (input: RequestInfo | URL, init?: RequestInit) => {
    seen.push({ url: String(input), init: init || {} });
    const next = responses[Math.min(seen.length - 1, responses.length - 1)];
    if (next instanceof Error) throw next;
    return next.clone();
  }) as typeof fetch;
  return { fetch: fetchStub, seen };
}

const CLOUDFLARE_PAGE = "<html><title>Just a moment...</title><script src='/cdn-cgi/challenge-platform/h/b'></script> Cloudflare</html>";

describe("http client", () => {
  test("sends browser headers and an Accept-Language from the plugin's languages", async () => {
    const { fetch, seen } = scripted([new Response("ok")]);
    const http = createHttpClient({ baseUrl: "https://s.example", languages: ["de"], fetch });
    await http.get("/page", { referer: "https://s.example/", headers: { "X-Test": "1" } });
    const headers = seen[0].init.headers as Record<string, string>;
    expect(seen[0].url).toBe("https://s.example/page");
    expect(headers["User-Agent"]).toContain("Mozilla/5.0");
    expect(headers["Accept-Language"]).toBe("de-DE,de;q=0.9,en;q=0.8");
    expect(headers.Referer).toBe("https://s.example/");
    expect(headers["X-Test"]).toBe("1");
  });

  test("falls back to English", () => {
    expect(acceptLanguage(undefined)).toBe("en-US,en;q=0.9");
    expect(acceptLanguage(["fr-CA"])).toBe("fr-CA,fr;q=0.9,en;q=0.8");
  });

  test("reads text, json and html from one body", async () => {
    const { fetch } = scripted([new Response('{"a":1}')]);
    const response = await createHttpClient({ fetch }).get("https://x.example/");
    expect(await response.json<object>()).toEqual({ a: 1 });
    expect(await response.text()).toBe('{"a":1}');
    expect(response.status).toBe(200);
    expect(response.response()).toBeInstanceOf(Response);
    const page = await createHttpClient({ fetch: scripted([new Response("<p class='x'>hi</p>")]).fetch }).get("https://x.example/");
    expect((await page.html()).querySelector("p.x")?.text).toBe("hi");
  });

  test("retries 5xx and network errors, then succeeds", async () => {
    const { fetch, seen } = scripted([new Response("", { status: 502 }), new Error("socket hang up"), new Response("fine")]);
    const response = await createHttpClient({ fetch }).get("https://x.example/");
    expect(await response.text()).toBe("fine");
    expect(seen).toHaveLength(3);
  });

  test("gives up on persistent 5xx with a source_offline error", async () => {
    const { fetch, seen } = scripted([new Response("", { status: 500 })]);
    const failure = await createHttpClient({ fetch }).get("https://x.example/").catch((error) => error);
    expect(failure).toBeInstanceOf(PluginSourceError);
    expect(failure.code).toBe("source_offline");
    expect(failure.status).toBe(500);
    expect(seen).toHaveLength(3);
  });

  test("reports an unreachable site as source_offline", async () => {
    const { fetch, seen } = scripted([new Error("refused")]);
    const failure = await createHttpClient({ fetch }).get("https://x.example/", { retries: 1 }).catch((error) => error);
    expect(failure).toBeInstanceOf(PluginSourceError);
    expect(failure.message).toContain("refused");
    expect(seen).toHaveLength(2);
  });

  test("does not retry 4xx", async () => {
    const { fetch, seen } = scripted([new Response("", { status: 404 })]);
    const failure = await createHttpClient({ fetch }).get("https://x.example/").catch((error) => error);
    expect(failure).toBeInstanceOf(HttpError);
    expect(failure.status).toBe(404);
    expect(seen).toHaveLength(1);
  });

  test("returns error statuses when asked to", async () => {
    const { fetch } = scripted([new Response("missing", { status: 404 })]);
    const response = await createHttpClient({ fetch }).get("https://x.example/", { allow_error_status: true });
    expect(response.status).toBe(404);
    expect(await response.text()).toBe("missing");
  });

  test("maps 429 to a rate limit error", async () => {
    const { fetch } = scripted([new Response("", { status: 429 })]);
    const failure = await createHttpClient({ fetch }).get("https://x.example/").catch((error) => error);
    expect(failure).toBeInstanceOf(PluginRateLimitError);
    expect(failure.code).toBe("rate_limited");
  });

  test("a Cloudflare challenge on 403 or 503 throws PluginBlockedError without retrying", async () => {
    for (const status of [403, 503]) {
      const { fetch, seen } = scripted([new Response(CLOUDFLARE_PAGE, { status })]);
      const failure = await createHttpClient({ fetch }).get("https://x.example/").catch((error) => error);
      expect(failure).toBeInstanceOf(PluginBlockedError);
      expect(failure.code).toBe("blocked");
      expect(seen).toHaveLength(1);
    }
  });

  test("a plain 403 is not a Cloudflare block", async () => {
    const { fetch } = scripted([new Response("forbidden", { status: 403 })]);
    const failure = await createHttpClient({ fetch }).get("https://x.example/").catch((error) => error);
    expect(failure).toBeInstanceOf(HttpError);
  });

  test("posts json and form bodies", async () => {
    const { fetch, seen } = scripted([new Response("{}")]);
    const http = createHttpClient({ fetch });
    await http.post("https://x.example/api", { json: { a: 1 } });
    await http.post("https://x.example/api", { body: { token: "a b", n: "1" } });
    const jsonHeaders = seen[0].init.headers as Record<string, string>;
    const formHeaders = seen[1].init.headers as Record<string, string>;
    expect(seen[0].init.method).toBe("POST");
    expect(jsonHeaders["Content-Type"]).toBe("application/json");
    expect(seen[0].init.body).toBe('{"a":1}');
    expect(formHeaders["Content-Type"]).toBe("application/x-www-form-urlencoded");
    expect(seen[1].init.body).toBe("token=a+b&n=1");
  });

  test("finalUrl returns the redirect target, or the input on failure", async () => {
    const followRedirect = (async () => {
      const redirected = new Response("");
      Object.defineProperty(redirected, "url", { value: "https://hoster.example/e/1" });
      return redirected;
    }) as unknown as typeof fetch;
    expect(await createHttpClient({ fetch: followRedirect }).finalUrl("https://x.example/r?t=1")).toBe("https://hoster.example/e/1");
    expect(await createHttpClient({ fetch: scripted([new Error("down")]).fetch }).finalUrl("https://x.example/r")).toBe("https://x.example/r");
  });

  test("absolute resolves against the base url", () => {
    const http = createHttpClient({ baseUrl: "https://s.example/de" });
    expect(http.absolute("/serie/a")).toBe("https://s.example/serie/a");
    expect(http.absolute("serie/a")).toBe("https://s.example/de/serie/a");
    expect(http.absolute("//cdn.example/a")).toBe("https://cdn.example/a");
    expect(http.absolute("https://other.example/a")).toBe("https://other.example/a");
    expect(() => createHttpClient({}).absolute("/a")).toThrow("no base_url");
  });
});

describe("context", () => {
  const definition = definePlugin({
    id: "demo",
    name: "Demo",
    version: "1",
    base_url: "https://demo.example/",
    settings: [
      { key: "TOKEN", label: "Token", type: "string" },
      { key: "REGION", label: "Region", type: "string", default: "eu" },
    ],
  });

  test("uses the declared base url, without trailing slash", () => {
    expect(effectiveBaseUrl(definition, {})).toBe("https://demo.example");
  });

  test("OTTER_BASE_URL overrides it and ignores blanks", () => {
    expect(effectiveBaseUrl(definition, { OTTER_BASE_URL: "https://mirror.example/" })).toBe("https://mirror.example");
    expect(effectiveBaseUrl(definition, { OTTER_BASE_URL: "  " })).toBe("https://demo.example");
    const ctx = createContext(definition, { env: { OTTER_BASE_URL: "https://mirror.example" } });
    expect(ctx.http.absolute("/x")).toBe("https://mirror.example/x");
    expect(ctx.plugin.base_url).toBe("https://mirror.example");
  });

  test("exposes declared settings with defaults", () => {
    expect(createContext(definition, { env: { TOKEN: "abc", UNRELATED: "x" } }).settings).toEqual({ TOKEN: "abc", REGION: "eu" });
  });

  test("list wraps items", () => {
    expect(createContext(definition, { env: {} }).list([1, 2])).toEqual({ object: "list", data: [1, 2] });
  });
});
