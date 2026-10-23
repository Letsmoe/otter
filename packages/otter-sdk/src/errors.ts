/** Why a plugin call failed, in terms the backend can act on. */
export type ErrorCode = "blocked" | "source_offline" | "extraction_failed" | "rate_limited" | "dead_link";

export interface OtterErrorOptions {
  code?: ErrorCode;
  status?: number;
}

/** Base class of every error the SDK throws; `runPlugin` reports its `code` to the backend. */
export class OtterError extends Error {
  readonly type: string;
  readonly code?: ErrorCode;
  readonly status?: number;

  constructor(type: string, message: string, options: OtterErrorOptions = {}) {
    super(message);
    this.name = type;
    this.type = type;
    this.code = options.code;
    this.status = options.status;
  }
}

/** The site refused us, typically with a Cloudflare challenge. */
export class PluginBlockedError extends OtterError {
  constructor(message: string, status?: number) {
    super("blocked_error", message, { code: "blocked", status });
  }
}

/** The site or a hoster is down, or a link no longer yields a stream. */
export class PluginSourceError extends OtterError {
  constructor(code: "source_offline" | "extraction_failed", message: string, status?: number) {
    super("source_error", message, { code, status });
  }
}

/**
 * The hoster says the file is gone: it was deleted or never existed. Unlike
 * `PluginSourceError` this is permanent, so the backend drops the link. Throw
 * it only when the hoster answers explicitly for that one link, never because
 * a request failed, was blocked or the extractor found nothing.
 */
export class PluginDeadLinkError extends OtterError {
  constructor(message: string, status?: number) {
    super("dead_link_error", message, { code: "dead_link", status });
  }
}

export class PluginRateLimitError extends OtterError {
  constructor(message: string, status?: number) {
    super("rate_limit_error", message, { code: "rate_limited", status });
  }
}

/** A response with a status the plugin did not expect, and no more specific meaning. */
export class HttpError extends OtterError {
  constructor(message: string, status: number) {
    super("http_error", message, { status });
  }
}
