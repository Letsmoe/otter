import {
  PluginSourceError,
  type PluginContext,
  type SourceInput,
  type SourceRetrieveParams,
} from "@neoworks-dev/otter-sdk";
import { cookieHeaderOf, cookiesFromSetCookie, type CookieJar } from "./cookies.ts";
import { chipIndexOf, csrfTokenOf, pageUrlOf, providerChipsOf, type ProviderChip } from "./site.ts";

/** The movie page's session: its cookies and the tokens the page hands out. */
interface PageSession {
  pageUrl: string;
  origin: string;
  cookies: CookieJar;
  csrfToken: string;
  chips: ProviderChip[];
}

/**
 * Turns a chip's stream URL into the hoster link: the session is opened on the
 * movie page, the chip's token is minted with it, and the mint redirects to the hoster.
 */
export async function retrieveSource({ url }: SourceRetrieveParams, ctx: PluginContext): Promise<SourceInput> {
  const session = await openSession(pageUrlOf(url), ctx);
  const chip = chipOf(session, chipIndexOf(url));
  const mintToken = await requestMintToken(chip, session, ctx);
  const hosterUrl = await hosterUrlOf(mintToken, session, ctx);
  return ctx.hosters.retrieve(hosterUrl, ctx);
}

function chipOf(session: PageSession, chipIndex: number | undefined): ProviderChip {
  const chip = session.chips.find((candidate) => candidate.index === chipIndex);
  if (!chip) throw new PluginSourceError("extraction_failed", `no provider chip ${chipIndex} on ${session.pageUrl}`);
  return chip;
}

async function openSession(pageUrl: string, ctx: PluginContext): Promise<PageSession> {
  const response = await ctx.http.get(pageUrl);
  const root = await response.html();
  const cookies = cookiesFromSetCookie(response.response().headers.get("set-cookie") || "");
  return {
    pageUrl,
    origin: new URL(pageUrl).origin,
    cookies,
    csrfToken: csrfTokenOf(root),
    chips: providerChipsOf(root),
  };
}

/** Both the CSRF meta token and the decoded XSRF cookie are sent, as the page's own script does. */
function sessionHeaders(session: PageSession): Record<string, string> {
  const xsrfCookie = session.cookies["XSRF-TOKEN"] || "";
  return {
    Cookie: cookieHeaderOf(session.cookies),
    Referer: session.pageUrl,
    Origin: session.origin,
    "X-CSRF-TOKEN": session.csrfToken,
    "X-XSRF-TOKEN": decodeURIComponent(xsrfCookie),
    "X-Requested-With": "XMLHttpRequest",
    Accept: "application/json",
  };
}

async function requestMintToken(chip: ProviderChip, session: PageSession, ctx: PluginContext): Promise<string> {
  const response = await ctx.http.post(`${session.origin}/n`, {
    json: { p: chip.playToken },
    headers: sessionHeaders(session),
  });
  const body = await response.json<{ x?: string }>();
  if (!body.x) throw new PluginSourceError("extraction_failed", `${session.pageUrl} minted no link for ${chip.provider}`);
  return body.x;
}

/** The mint URL answers with a redirect to the hoster; the redirect is read, not followed. */
async function hosterUrlOf(mintToken: string, session: PageSession, ctx: PluginContext): Promise<string> {
  const response = await ctx.http.get(`${session.origin}/n/${mintToken}`, {
    headers: sessionHeaders(session),
    follow_redirects: false,
  });
  const location = response.response().headers.get("location");
  if (!location) throw new PluginSourceError("extraction_failed", `${session.pageUrl} did not redirect to a hoster`);
  return ctx.http.absolute(location);
}
