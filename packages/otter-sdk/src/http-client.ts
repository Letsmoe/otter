import { parse, type HTMLElement } from "node-html-parser";
import { HttpError, PluginBlockedError, PluginRateLimitError, PluginSourceError } from "./errors.ts";
import { resolveAgainst } from "./url.ts";
import { BROWSER_HEADERS, fetchViaSolver, hasSolver } from "./http.ts";

const DEFAULT_TIMEOUT_MS = 15_000;
const DEFAULT_RETRIES = 2;
const RETRY_BACKOFF_MS = 250;

const CLOUDFLARE_MARKERS = [
  "cf-chl-",
  "_cf_chl_opt",
  "challenge-platform",
  "cf-browser-verification",
  "challenges.cloudflare.com",
  "attention required! | cloudflare",
];

export interface RequestOptions {
  headers?: Record<string, string>;
  referer?: string;
  /** Default 15000. */
  timeout_ms?: number;
  /** Extra attempts after a network error or 5xx. Default 2. */
  retries?: number;
  /** Return 4xx/5xx responses instead of throwing; a Cloudflare block still throws. */
  allow_error_status?: boolean;
  /** Default true. When false, a redirect is returned as is and its target is in the Location header. */
  follow_redirects?: boolean;
}

export interface PostOptions extends RequestOptions {
  /** A raw body, or an object sent as application/x-www-form-urlencoded. */
  body?: string | Record<string, string>;
  /** Sent as application/json; wins over `body`. */
  json?: unknown;
}

/** A completed request. The body is read once and cached, so the readers can be mixed freely. */
export interface HttpResponse {
  readonly status: number;
  /** Final URL after redirects. */
  readonly url: string;
  text(): Promise<string>;
  json<Data = unknown>(): Promise<Data>;
  html(): Promise<HTMLElement>;
  response(): Response;
}

export interface HttpClient {
  get(url: string, options?: RequestOptions): Promise<HttpResponse>;
  post(url: string, options?: PostOptions): Promise<HttpResponse>;
  /** Follows redirects and returns the destination; returns `url` itself when the request fails. */
  finalUrl(url: string, options?: RequestOptions): Promise<string>;
  /** Resolves a path or URL against the plugin's (possibly overridden) base_url. */
  absolute(path: string): string;
}

export interface HttpClientConfig {
  baseUrl?: string;
  languages?: string[];
  fetch?: typeof fetch;
}

interface PreparedRequest {
  url: string;
  init: RequestInit;
  retries: number;
  timeoutMs: number;
  allowErrorStatus: boolean;
}

/** A response whose body may already have been read. */
interface RawResult {
  response: Response;
  bodyText?: string;
}

class FetchedResponse implements HttpResponse {
  private bodyText?: string;

  constructor(
    private readonly rawResponse: Response,
    preloadedText?: string,
  ) {
    this.bodyText = preloadedText;
  }

  get status(): number {
    return this.rawResponse.status;
  }

  get url(): string {
    return this.rawResponse.url;
  }

  async text(): Promise<string> {
    if (this.bodyText === undefined) {
      this.bodyText = await this.rawResponse.text();
    }
    return this.bodyText;
  }

  async json<Data = unknown>(): Promise<Data> {
    return JSON.parse(await this.text()) as Data;
  }

  async html(): Promise<HTMLElement> {
    return parse(await this.text());
  }

  response(): Response {
    return this.rawResponse;
  }
}

/** Accept-Language for the plugin's first language, falling back to English. */
export function acceptLanguage(languages: string[] | undefined): string {
  const english = "en-US,en;q=0.9";
  if (!languages || languages.length === 0) return english;
  const language = languages[0];
  if (language === "en") return english;
  const base = language.split("-")[0];
  if (language === base) return `${language}-${language.toUpperCase()},${language};q=0.9,en;q=0.8`;
  return `${language},${base};q=0.9,en;q=0.8`;
}

export function isCloudflareChallenge(status: number, headers: Headers, bodyText: string): boolean {
  if (status !== 403 && status !== 503) return false;
  if (headers.get("cf-mitigated") === "challenge") return true;
  const lowered = bodyText.toLowerCase();
  if (lowered.includes("just a moment") && lowered.includes("cloudflare")) return true;
  return CLOUDFLARE_MARKERS.some((marker) => lowered.includes(marker));
}

/** The error a response status stands for when the caller did not ask for error responses. */
export function errorForStatus(url: string, status: number): Error {
  const message = `HTTP ${status} fetching ${url}`;
  if (status === 429) return new PluginRateLimitError(message, status);
  if (status >= 500) return new PluginSourceError("source_offline", message, status);
  return new HttpError(message, status);
}

function sleep(milliseconds: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, milliseconds));
}

function formEncode(fields: Record<string, string>): string {
  return new URLSearchParams(fields).toString();
}

export function createHttpClient(config: HttpClientConfig): HttpClient {
  return new PluginHttpClient(config);
}

class PluginHttpClient implements HttpClient {
  private readonly fetchImplementation: typeof fetch;

  constructor(private readonly config: HttpClientConfig) {
    this.fetchImplementation = config.fetch ?? fetch;
  }

  get(url: string, options: RequestOptions = {}): Promise<HttpResponse> {
    return this.send(this.prepare("GET", url, options));
  }

  post(url: string, options: PostOptions = {}): Promise<HttpResponse> {
    const request = this.prepare("POST", url, options);
    this.attachBody(request.init, options);
    return this.send(request);
  }

  async finalUrl(url: string, options: RequestOptions = {}): Promise<string> {
    const request = this.prepare("GET", url, options);
    try {
      const response = await this.fetchImplementation(request.url, this.withTimeout(request));
      return response.url || request.url;
    } catch {
      return request.url;
    }
  }

  absolute(path: string): string {
    if (/^https?:\/\//i.test(path)) return path;
    if (path.startsWith("//")) return "https:" + path;
    if (!this.config.baseUrl) {
      throw new Error(`cannot resolve "${path}": the plugin declares no base_url`);
    }
    return resolveAgainst(this.config.baseUrl + "/", path);
  }

  private prepare(method: string, path: string, options: RequestOptions): PreparedRequest {
    const headers: Record<string, string> = {
      "User-Agent": BROWSER_HEADERS["User-Agent"],
      Accept: BROWSER_HEADERS.Accept,
      "Accept-Language": acceptLanguage(this.config.languages),
    };
    if (options.referer) headers.Referer = options.referer;
    Object.assign(headers, options.headers);

    let retries = DEFAULT_RETRIES;
    if (options.retries !== undefined) retries = options.retries;
    const init: RequestInit = { method, headers };
    if (options.follow_redirects === false) init.redirect = "manual";
    return {
      url: this.absolute(path),
      init,
      retries,
      timeoutMs: options.timeout_ms || DEFAULT_TIMEOUT_MS,
      allowErrorStatus: options.allow_error_status === true,
    };
  }

  private withTimeout(request: PreparedRequest): RequestInit {
    return { ...request.init, signal: AbortSignal.timeout(request.timeoutMs) };
  }

  private attachBody(init: RequestInit, options: PostOptions): void {
    const headers = init.headers as Record<string, string>;
    if (options.json !== undefined) {
      headers["Content-Type"] = "application/json";
      init.body = JSON.stringify(options.json);
      return;
    }
    if (typeof options.body === "string") {
      init.body = options.body;
      return;
    }
    if (options.body) {
      headers["Content-Type"] = "application/x-www-form-urlencoded";
      init.body = formEncode(options.body);
    }
  }

  private async send(request: PreparedRequest): Promise<HttpResponse> {
    const raw = await this.fetchWithRetries(request);
    const status = raw.response.status;
    if (status >= 200 && status < 400) return new FetchedResponse(raw.response, raw.bodyText);

    const bodyText = await this.readBody(raw);
    if (isCloudflareChallenge(status, raw.response.headers, bodyText)) {
      return this.solveChallenge(request, raw.response);
    }
    if (request.allowErrorStatus) return new FetchedResponse(raw.response, bodyText);
    throw errorForStatus(request.url, status);
  }

  private async readBody(raw: RawResult): Promise<string> {
    if (raw.bodyText !== undefined) return raw.bodyText;
    return raw.response.text();
  }

  private async solveChallenge(request: PreparedRequest, response: Response): Promise<HttpResponse> {
    const message = `${request.url} is behind a Cloudflare challenge`;
    if (request.init.method !== "GET" || !hasSolver()) {
      throw new PluginBlockedError(message, response.status);
    }
    try {
      const solvedHtml = await fetchViaSolver(request.url);
      return new FetchedResponse(response, solvedHtml);
    } catch (error) {
      const reason = error instanceof Error ? error.message : String(error);
      throw new PluginBlockedError(`${message} and the solver failed: ${reason}`, response.status);
    }
  }

  /** Retries network errors and 5xx; returns the last response once attempts run out. */
  private async fetchWithRetries(request: PreparedRequest): Promise<RawResult> {
    let lastFailure = "";
    for (let attempt = 0; attempt <= request.retries; attempt++) {
      if (attempt > 0) await sleep(RETRY_BACKOFF_MS * attempt);
      try {
        const raw = await this.fetchOnce(request);
        if (!this.shouldRetry(raw) || attempt === request.retries) return raw;
      } catch (error) {
        lastFailure = error instanceof Error ? error.message : String(error);
      }
    }
    throw new PluginSourceError("source_offline", `${request.url} is unreachable: ${lastFailure}`);
  }

  /** A Cloudflare challenge answers 503 too, and retrying it cannot help. */
  private shouldRetry(raw: RawResult): boolean {
    const status = raw.response.status;
    if (status < 500) return false;
    return !isCloudflareChallenge(status, raw.response.headers, raw.bodyText || "");
  }

  /** Reads the body of a 503 up front, because that is where a challenge page shows. */
  private async fetchOnce(request: PreparedRequest): Promise<RawResult> {
    const response = await this.fetchImplementation(request.url, this.withTimeout(request));
    if (response.status !== 503) return { response };
    return { response, bodyText: await response.text() };
  }
}
