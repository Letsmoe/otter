// Stream pages are /stream/<slug>; episodes use "<series slug>-s<NN>e<NN>".
const EPISODE_SUFFIX = /[-_]s\d+e\d+$/i;

export function slugFromHref(href: string): string {
  const afterStreamPrefix = href.split("/stream/").pop() || "";
  return afterStreamPrefix.replace(/\/.*$/, "");
}

export function isEpisodeSlug(slug: string): boolean {
  return EPISODE_SUFFIX.test(slug);
}

export function seriesSlugOf(episodeSlug: string): string {
  return episodeSlug.replace(EPISODE_SUFFIX, "");
}

export function episodeStreamPath(seriesSlug: string, seasonNumber: number, episodeNumber: number): string {
  return `/stream/${seriesSlug}-s${padNumber(seasonNumber)}e${padNumber(episodeNumber)}`;
}

function padNumber(number: number): string {
  return String(number).padStart(2, "0");
}
