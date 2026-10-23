export type CookieJar = Record<string, string>;

const COOKIE_PAIR = /(?:^|,\s*)([^=;,\s]+)=([^;,]*)/;

/**
 * Reads the name=value pairs of a Set-Cookie header. The runtime joins several
 * Set-Cookie headers with ", ", and Expires values contain commas too; only a
 * comma followed by "name=" starts a new cookie.
 */
export function cookiesFromSetCookie(setCookieHeader: string): CookieJar {
  const jar: CookieJar = {};
  const pattern = new RegExp(COOKIE_PAIR.source, "g");
  let match = pattern.exec(setCookieHeader);
  while (match) {
    jar[match[1]] = match[2];
    match = pattern.exec(setCookieHeader);
  }
  return jar;
}

export function cookieHeaderOf(jar: CookieJar): string {
  return Object.keys(jar)
    .map((name) => `${name}=${jar[name]}`)
    .join("; ");
}
