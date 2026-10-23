// "/Stream/Running_Point.html" -> "Running_Point"
export function slugFromHref(href: string): string {
  return href.replace(/^.*\/Stream\//, "").replace(/\.html$/, "");
}

/** Mirror endpoint of one hoster for a movie. */
export function mirrorPath(slug: string, hosterId: string): string {
  return `/aGET/Mirror/${slug}&Hoster=${hosterId}`;
}

/** Mirror endpoint of one hoster for a series episode. */
export function episodeMirrorPath(slug: string, hosterId: string, seasonNumber: number, episodeNumber: number): string {
  return `${mirrorPath(slug, hosterId)}&Season=${seasonNumber}&Episode=${episodeNumber}`;
}

/** Hoster list of one episode; `seriesQuery` is the season select's "?Addr=<slug>&SeriesID=<id>". */
export function mirrorsByEpisodePath(seriesQuery: string, seasonNumber: number, episodeNumber: number): string {
  return `/aGET/MirrorByEpisode/${seriesQuery}&Season=${seasonNumber}&Episode=${episodeNumber}`;
}
