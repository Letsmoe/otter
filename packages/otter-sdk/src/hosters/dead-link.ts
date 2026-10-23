import { HttpError, PluginDeadLinkError } from "../errors.ts";
import type { PluginContext } from "../definition.ts";
import { errorForStatus, type HttpResponse, type RequestOptions } from "../http-client.ts";

const GONE_STATUSES = [404, 410];
const API_MISSING_MESSAGE = /not found|missing|deleted|removed|does not exist/i;

function isGoneStatus(status: number): boolean {
  return GONE_STATUSES.includes(status);
}

/**
 * Fetches the page of the link itself. A 404 or 410 there means the hoster
 * dropped the file; every other failure (challenge, 403, 429, 5xx) keeps its
 * own error. Do not use it for secondary requests such as assets or APIs.
 */
export async function getLinkPage(url: string, ctx: PluginContext, options: RequestOptions = {}): Promise<HttpResponse> {
  try {
    return await ctx.http.get(url, options);
  } catch (error) {
    if (error instanceof HttpError && error.status !== undefined && isGoneStatus(error.status)) {
      throw new PluginDeadLinkError(`${url} no longer exists (HTTP ${error.status})`, error.status);
    }
    throw error;
  }
}

/** For hosters that answer a missing file with a normal 200 page: throws when the page matches `missingPage`. */
export function assertFilePresent(html: string, missingPage: RegExp, url: string): void {
  if (missingPage.test(html)) throw new PluginDeadLinkError(`${url} says the file does not exist`);
}

function apiErrorMessage(body: string): string {
  try {
    const parsed = JSON.parse(body);
    if (typeof parsed.error === "string") return parsed.error;
    return "";
  } catch {
    return "";
  }
}

/** Whether a hoster API answered "no such video" for the link: a 404/410 whose JSON error names the missing video. */
export function apiReportsMissingFile(status: number, body: string): boolean {
  if (!isGoneStatus(status)) return false;
  return API_MISSING_MESSAGE.test(apiErrorMessage(body));
}

/** Throws the dead link error when a hoster API reported a missing file, otherwise the error the status stands for. */
export function throwForApiFailure(url: string, status: number, body: string): never {
  if (apiReportsMissingFile(status, body)) {
    throw new PluginDeadLinkError(`${url} reports the video as missing (HTTP ${status})`, status);
  }
  throw errorForStatus(url, status);
}
