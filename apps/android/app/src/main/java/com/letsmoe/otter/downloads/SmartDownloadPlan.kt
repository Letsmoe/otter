package com.letsmoe.otter.downloads

private const val BYTES_PER_GIGABYTE = 1024L * 1024 * 1024
private const val MINUTES_PER_HOUR = 60.0

// What Smart Downloads assumes when TMDB doesn't know how long something is.
private const val UNKNOWN_EPISODE_MINUTES = 45
private const val UNKNOWN_MOVIE_MINUTES = 100

// How many recommended films are kept ready next to what the profile is watching.
private const val RECOMMENDED_FILMS = 3

/** The picture quality smart downloads are saved in, with how much space an hour of it takes. */
enum class SmartQuality(val label: String, val height: Int, val gigabytesPerHour: Double) {
    HD720("720p", 720, 1.0),
    HD1080("1080p", 1080, 2.5),
}

fun gigabytesToBytes(gigabytes: Int): Long = gigabytes * BYTES_PER_GIGABYTE

/** About how many bytes [runtimeMinutes] of video take in [quality]. */
fun estimatedBytes(runtimeMinutes: Int, quality: SmartQuality): Long {
    val hours = runtimeMinutes / MINUTES_PER_HOUR
    return (hours * quality.gigabytesPerHour * BYTES_PER_GIGABYTE).toLong()
}

/** How many hours of video a budget of [gigabytes] holds in [quality], roughly. */
fun playtimeHours(gigabytes: Int, quality: SmartQuality): Double {
    return gigabytes / quality.gigabytesPerHour
}

/** Something the profile might want offline soon; [estimatedBytes] is what it is expected to take. */
data class SmartCandidate(val mediaId: String, val estimatedBytes: Long)

/** A smart download that exists; [bytes] is its size, or the estimate while it is still being fetched. */
data class SmartDownload(val downloadId: String, val mediaId: String, val bytes: Long)

data class SmartPlan(val delete: List<SmartDownload>, val start: List<SmartCandidate>)

/**
 * What to change so the smart downloads stay within the budget: watched ones go, then candidates
 * (most wanted first) are added while they fit. Media that already has a download of any kind
 * (manual ones included) or that the viewer removed before is in [skipped] and never added again.
 * Manual downloads don't count against the budget and are never in the plan.
 */
fun planSmartDownloads(
    budgetBytes: Long,
    existing: List<SmartDownload>,
    watchedMediaIds: Set<String>,
    candidates: List<SmartCandidate>,
    skipped: Set<String>,
): SmartPlan {
    val (watched, kept) = existing.partition { download -> download.mediaId in watchedMediaIds }
    var remaining = budgetBytes - kept.sumOf { download -> download.bytes }
    val start = mutableListOf<SmartCandidate>()
    for (candidate in candidates) {
        if (candidate.mediaId in skipped || start.any { added -> added.mediaId == candidate.mediaId }) {
            continue
        }
        if (candidate.estimatedBytes > remaining) {
            continue
        }
        remaining -= candidate.estimatedBytes
        start.add(candidate)
    }
    return SmartPlan(delete = watched, start = start)
}

/** What the continue-watching list says about one title. */
data class ContinueSource(
    val isSeries: Boolean,
    val mediaId: String,
    val runtimeMinutes: Int?,
    /** The episode being watched or up next (series only), with the one after it. */
    val episodeId: String? = null,
    val episodeMinutes: Int? = null,
    val nextEpisodeId: String? = null,
    val nextEpisodeMinutes: Int? = null,
)

/** A recommended movie and its runtime when known. */
data class RecommendedMovie(val mediaId: String, val runtimeMinutes: Int?)

/**
 * The wish list in order: the next one or two episodes of each series in progress and movies not
 * finished, then a few recommended films.
 */
fun smartCandidates(continuing: List<ContinueSource>, recommended: List<RecommendedMovie>, quality: SmartQuality): List<SmartCandidate> {
    val candidates = mutableListOf<SmartCandidate>()
    for (source in continuing) {
        candidates.addAll(continuingCandidates(source, quality))
    }
    for (movie in recommended.take(RECOMMENDED_FILMS)) {
        candidates.add(candidateOf(movie.mediaId, movie.runtimeMinutes, UNKNOWN_MOVIE_MINUTES, quality))
    }
    return candidates
}

private fun continuingCandidates(source: ContinueSource, quality: SmartQuality): List<SmartCandidate> {
    if (!source.isSeries) {
        return listOf(candidateOf(source.mediaId, source.runtimeMinutes, UNKNOWN_MOVIE_MINUTES, quality))
    }
    val candidates = mutableListOf<SmartCandidate>()
    source.episodeId?.let { id -> candidates.add(candidateOf(id, source.episodeMinutes, UNKNOWN_EPISODE_MINUTES, quality)) }
    source.nextEpisodeId?.let { id -> candidates.add(candidateOf(id, source.nextEpisodeMinutes, UNKNOWN_EPISODE_MINUTES, quality)) }
    return candidates
}

private fun candidateOf(mediaId: String, runtimeMinutes: Int?, unknownMinutes: Int, quality: SmartQuality): SmartCandidate {
    var minutes = unknownMinutes
    if (runtimeMinutes != null && runtimeMinutes > 0) {
        minutes = runtimeMinutes
    }
    return SmartCandidate(mediaId, estimatedBytes(minutes, quality))
}

/** The video height a link's quality names ("1080p" is 1080, "4K" is 2160); null for "HLS" and the like. */
fun heightOfQuality(quality: String?): Int? {
    val normalized = quality?.trim()?.lowercase() ?: return null
    if (normalized == "4k" || normalized == "uhd") {
        return 2160
    }
    if (!normalized.endsWith("p")) {
        return null
    }
    return normalized.removeSuffix("p").toIntOrNull()
}

/** A link on offer: its id and quality as the backend names it, and whether it was checked to play. */
data class DownloadLink(val id: String, val quality: String?, val playable: Boolean?)

/**
 * The link whose quality is the highest not above [maxHeight]; links known to be dead and links of
 * unknown quality are not picked. Null when none is, so the backend chooses itself.
 */
fun pickLinkFor(links: List<DownloadLink>, maxHeight: Int): DownloadLink? {
    return links
        .filter { link -> link.playable != false }
        .filter { link ->
            val height = heightOfQuality(link.quality)
            height != null && height <= maxHeight
        }
        .maxByOrNull { link -> heightOfQuality(link.quality) ?: 0 }
}
