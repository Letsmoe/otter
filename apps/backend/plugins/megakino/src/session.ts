import type { PluginContext } from "@neoworks-dev/otter-sdk";

const HANDSHAKE_PATH = "/index.php?yg=token";
const SESSION_COOKIE = /yg_token=([^;,\s]+)/;

/** Headers that carry the site's session: pages are a JS stub until the handshake's cookie is sent back. */
export async function sessionHeadersOf(ctx: PluginContext): Promise<Record<string, string>> {
  const handshake = await ctx.http.get(HANDSHAKE_PATH);
  const setCookie = handshake.response().headers.get("set-cookie") || "";
  const match = setCookie.match(SESSION_COOKIE);
  if (!match) return {};
  return { Cookie: `yg_token=${match[1]}` };
}
