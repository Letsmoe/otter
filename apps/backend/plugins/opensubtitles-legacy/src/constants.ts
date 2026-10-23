export const API_BASE = "https://rest.opensubtitles.org";
export const MAX_PER_LANGUAGE = 10;
/** Languages asked for at once, so one title costs a handful of requests at most. */
export const MAX_LANGUAGES = 4;
/** The API only needs some user agent; this one is its documented test value. */
export const USER_AGENT = "TemporaryUserAgent";
export const DOWNLOAD_HEADERS = { "User-Agent": USER_AGENT, "X-User-Agent": USER_AGENT };
export const SEARCH_HEADERS = { ...DOWNLOAD_HEADERS, Accept: "application/json" };
