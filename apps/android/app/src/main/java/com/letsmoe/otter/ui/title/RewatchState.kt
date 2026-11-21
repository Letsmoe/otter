package com.letsmoe.otter.ui.title

// Watched this far, an episode counts as finished; the same bar the backend uses.
private const val FINISHED_FRACTION = 0.9f

/** What the title page offers about rewatching. */
enum class RewatchOffer {
    NONE,

    /** The title was watched to the end and no rewatch is going. */
    START,

    /** A rewatch is going: it can be continued or stopped. */
    RUNNING,
}

fun rewatchOffer(finishedBefore: Boolean, rewatching: Boolean): RewatchOffer {
    if (rewatching) {
        return RewatchOffer.RUNNING
    }
    if (finishedBefore) {
        return RewatchOffer.START
    }
    return RewatchOffer.NONE
}

/** The `finished` flag of a title's latest rewatch; null when it never had one. */
fun isRewatchRunning(rewatchFinished: Boolean?): Boolean {
    if (rewatchFinished == null) {
        return false
    }
    return !rewatchFinished
}

/** Whether the furthest started episode was watched to its end and no episode follows it. */
fun isSeriesFinished(episodes: List<SeasonEpisode>): Boolean {
    val furthest = episodes.lastOrNull { episode -> episode.progress != null && episode.seasonNumber > 0 }
    if (furthest == null) {
        return false
    }
    val furthestProgress = furthest.progress
    if (furthestProgress == null || furthestProgress < FINISHED_FRACTION) {
        return false
    }
    return episodes.none { episode -> episode.id == furthest.nextEpisodeId }
}

/** The play button's words for a series while a rewatch is going. */
fun rewatchSeriesPlayLabel(upNext: UpNext?): String {
    if (upNext == null) {
        return "Continue rewatch"
    }
    if (upNext.resume) {
        return "Continue rewatch ${upNext.episode.code}"
    }
    return "Rewatch ${upNext.episode.code}"
}
