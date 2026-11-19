import { PluginBlockedError, PluginDeadLinkError } from "./errors.ts";

/** What the app does on the page before it watches for the hoster. */
export interface BrowseOptions {
  /** CSS selector of the element to click once the page has loaded, e.g. the link's button. */
  click?: string;
  /**
   * CSS selector of a form to submit once its captchas (Turnstile, Altcha, ...) are solved.
   * Then the first page off the site loading in any frame after the form was posted counts,
   * for sites that open the hoster in an iframe.
   */
  submit?: string;
}

/** Pages that need a real browser, opened in the Otter app's WebView. */
export interface Browser {
  /**
   * Opens `url` in the app, clicks and submits what `options` name, passes any captcha on
   * the way (hidden when it can, shown to the user only while they wait to play this link)
   * and resolves with the first URL off the page's site, e.g. the hoster. Throws
   * PluginBlockedError where no app can do that (a NAS backend, tests) and
   * PluginDeadLinkError when the page lacks the element to click.
   */
  open(url: string, options?: BrowseOptions): Promise<string>;
}

// Installed by the backend's embedded runtime while the phone app listens for challenges.
declare const __otterBrowse: ((url: string, options?: BrowseOptions) => Promise<string>) | undefined;

const BLOCKED_PREFIX = "blocked: ";
const DEAD_LINK_PREFIX = "dead_link: ";

export function createBrowser(): Browser {
  return {
    async open(url: string, options: BrowseOptions = {}): Promise<string> {
      if (typeof __otterBrowse !== "function") {
        throw new PluginBlockedError(`Opening ${url} needs the Otter app`);
      }
      try {
        return await __otterBrowse(url, options);
      } catch (error) {
        throw browseError(error);
      }
    },
  };
}

// The runtime prefixes failures with the code they stand for.
function browseError(error: unknown): unknown {
  const message = error instanceof Error ? error.message : String(error);
  if (message.startsWith(BLOCKED_PREFIX)) return new PluginBlockedError(message.slice(BLOCKED_PREFIX.length));
  if (message.startsWith(DEAD_LINK_PREFIX)) return new PluginDeadLinkError(message.slice(DEAD_LINK_PREFIX.length));
  return error;
}
