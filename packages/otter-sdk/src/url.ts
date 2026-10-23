// URL helpers that behave the same under Bun and in the backend's embedded
// runtime, whose URL class drops the port from `origin` and resolves relative
// paths without a leading slash against the root.

const ABSOLUTE_URL = /^[a-z][a-z0-9+.-]*:/i;
const URL_PARTS = /^([a-z][a-z0-9+.-]*:)\/\/([^/?#]+)([^?#]*)(\?[^#]*)?/i;

interface UrlParts {
  protocol: string;
  origin: string;
  pathname: string;
  search: string;
}

function partsOf(url: string): UrlParts {
  const parsed = URL_PARTS.exec(url);
  if (!parsed) throw new Error(`not an absolute URL: ${url}`);
  return {
    protocol: parsed[1],
    origin: `${parsed[1]}//${parsed[2]}`,
    pathname: parsed[3] || "/",
    search: parsed[4] || "",
  };
}

/** "https://host:8080" of an absolute URL, port included. */
export function originOf(url: string): string {
  return partsOf(url).origin;
}

/** Drops "." and ".." segments, keeping a query or fragment as is. */
function removeDotSegments(path: string): string {
  const suffixStart = path.search(/[?#]/);
  let pathname = path;
  let suffix = "";
  if (suffixStart !== -1) {
    pathname = path.slice(0, suffixStart);
    suffix = path.slice(suffixStart);
  }
  const kept: string[] = [];
  for (const segment of pathname.split("/")) {
    if (segment === "..") kept.pop();
    else if (segment !== ".") kept.push(segment);
  }
  const joined = kept.join("/");
  if (joined.startsWith("/")) return joined + suffix;
  return "/" + joined + suffix;
}

/** Resolves a link found on `baseUrl`'s page: absolute, scheme-relative, root-relative or relative. */
export function resolveAgainst(baseUrl: string, reference: string): string {
  if (ABSOLUTE_URL.test(reference)) return reference;
  const base = partsOf(baseUrl);
  if (reference.startsWith("//")) return base.protocol + reference;
  if (reference.startsWith("/")) return base.origin + removeDotSegments(reference);
  if (reference.startsWith("?")) return base.origin + base.pathname + reference;
  if (reference.startsWith("#")) return base.origin + base.pathname + base.search + reference;
  const directory = base.pathname.slice(0, base.pathname.lastIndexOf("/") + 1);
  return base.origin + removeDotSegments(directory + reference);
}
