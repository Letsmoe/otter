import { afterEach, describe, expect, test } from "bun:test";
import { createBrowser } from "../src/browser.ts";
import { PluginBlockedError } from "../src/errors.ts";

type BrowseGlobal = { __otterBrowse?: (url: string, options?: unknown) => Promise<string> };
const runtime = globalThis as BrowseGlobal;

afterEach(() => {
  delete runtime.__otterBrowse;
});

describe("ctx.browser.open", () => {
  test("is blocked where no app can open pages", async () => {
    await expect(createBrowser().open("https://s.to/r?t=abc")).rejects.toBeInstanceOf(PluginBlockedError);
  });

  test("resolves with the URL the app's WebView ended up on", async () => {
    runtime.__otterBrowse = async (url) => `https://voe.sx/e/for-${new URL(url).searchParams.get("t")}`;
    expect(await createBrowser().open("https://s.to/r?t=abc")).toBe("https://voe.sx/e/for-abc");
  });

  test("an unsolved check becomes a blocked error", async () => {
    runtime.__otterBrowse = async () => {
      throw new Error("blocked: the site's bot check was not solved: Closed by the user.");
    };
    const failure = createBrowser().open("https://s.to/r?t=abc");
    await expect(failure).rejects.toBeInstanceOf(PluginBlockedError);
    await expect(failure).rejects.toThrow("Closed by the user.");
  });

  test("passes click and submit on to the app", async () => {
    let seen: unknown;
    runtime.__otterBrowse = async (_url, options) => {
      seen = options;
      return "https://voe.sx/e/abc";
    };
    await createBrowser().open("https://s.to/serie/x/staffel-1/episode-1", { click: "#voe", submit: "#form" });
    expect(seen).toEqual({ click: "#voe", submit: "#form" });
  });

  test("a page without the link to click is a dead link", async () => {
    runtime.__otterBrowse = async () => {
      throw new Error("dead_link: the link is not on the page any more: no #voe");
    };
    await expect(createBrowser().open("https://s.to/x", { click: "#voe" })).rejects.toMatchObject({ code: "dead_link" });
  });

  test("other failures pass through unchanged", async () => {
    runtime.__otterBrowse = async () => {
      throw new Error("context canceled");
    };
    const failure = createBrowser().open("https://s.to/r?t=abc");
    await expect(failure).rejects.not.toBeInstanceOf(PluginBlockedError);
  });
});
