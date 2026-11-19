import { PluginBlockedError } from "./errors.ts";

/** Pages that need a real browser, opened in the Otter app's WebView. */
export interface Browser {
  /**
   * Opens `url` in the app, passes any captcha on the way (hidden when it can, shown to
   * the user only while they wait to play this link) and resolves with the first URL off
   * the page's site, e.g. the hoster a captcha-gated link page redirects to. Throws
   * PluginBlockedError where no app can do that (a NAS backend, tests).
   */
  open(url: string): Promise<string>;
}

// Installed by the backend's embedded runtime while the phone app listens for challenges.
declare const __otterBrowse: ((url: string) => Promise<string>) | undefined;

const BLOCKED_PREFIX = "blocked: ";

export function createBrowser(): Browser {
  return {
    async open(url: string): Promise<string> {
      if (typeof __otterBrowse !== "function") {
        throw new PluginBlockedError(`Opening ${url} needs the Otter app`);
      }
      try {
        return await __otterBrowse(url);
      } catch (error) {
        throw browseError(error);
      }
    },
  };
}

// The runtime prefixes failures to pass the page with "blocked: ".
function browseError(error: unknown): unknown {
  const message = error instanceof Error ? error.message : String(error);
  if (!message.startsWith(BLOCKED_PREFIX)) return error;
  return new PluginBlockedError(message.slice(BLOCKED_PREFIX.length));
}
