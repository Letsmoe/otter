package com.letsmoe.otter.filler

import android.content.Context
import androidx.core.content.edit
import com.letsmoe.otter.graphql.type.FillerKind

/** An episode of a series with its place in the order of the series and what a filler list says about it. */
interface SeriesEpisode {
    val id: String
    val seasonNumber: Int
    val episodeNumber: Int
    val filler: FillerKind?
}

/** The episode that follows, and how many filler episodes were jumped over to get there. */
data class FollowingEpisode<Episode>(val episode: Episode, val skippedFiller: Int)

/** Whether a filler list calls the episode filler. Mixed episodes hold canon too and are kept. */
fun SeriesEpisode.isFiller(): Boolean = filler == FillerKind.FILLER

/** The regular episodes (no specials) in the order they are watched. */
fun <Episode : SeriesEpisode> List<Episode>.inWatchingOrder(): List<Episode> {
    return filter { episode -> episode.seasonNumber > 0 }
        .sortedWith(compareBy({ episode -> episode.seasonNumber }, { episode -> episode.episodeNumber }))
}

/**
 * The episode after [currentId] in watching order, across seasons. With [skipFiller] filler episodes are
 * jumped over; null when nothing follows (or only filler does) or [currentId] is no regular episode.
 */
fun <Episode : SeriesEpisode> List<Episode>.following(currentId: String, skipFiller: Boolean): FollowingEpisode<Episode>? {
    val ordered = inWatchingOrder()
    val currentIndex = ordered.indexOfFirst { episode -> episode.id == currentId }
    if (currentIndex < 0) {
        return null
    }
    var skipped = 0
    for (candidate in ordered.drop(currentIndex + 1)) {
        if (skipFiller && candidate.isFiller()) {
            skipped++
            continue
        }
        return FollowingEpisode(candidate, skipped)
    }
    return null
}

/** The first episode to watch: the first regular one, or with [skipFiller] the first that isn't filler. */
fun <Episode : SeriesEpisode> List<Episode>.firstToWatch(skipFiller: Boolean): Episode? {
    val ordered = inWatchingOrder()
    if (!skipFiller) {
        return ordered.firstOrNull()
    }
    return ordered.firstOrNull { episode -> !episode.isFiller() }
}

/** Per profile and series: whether filler episodes are jumped over. Saved on this device only. */
object FillerSkipSettings {

    private lateinit var preferences: android.content.SharedPreferences

    fun initialize(context: Context) {
        preferences = context.getSharedPreferences("filler-skip", Context.MODE_PRIVATE)
    }

    fun skips(profileId: String, seriesId: String): Boolean {
        return preferences.getBoolean("$profileId|$seriesId", false)
    }

    fun set(profileId: String, seriesId: String, skipsFiller: Boolean) {
        preferences.edit { putBoolean("$profileId|$seriesId", skipsFiller) }
    }
}
