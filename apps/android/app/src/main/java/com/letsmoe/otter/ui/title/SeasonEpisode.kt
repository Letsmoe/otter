package com.letsmoe.otter.ui.title

import com.letsmoe.otter.filler.SeriesEpisode
import com.letsmoe.otter.filler.firstToWatch
import com.letsmoe.otter.filler.following
import com.letsmoe.otter.graphql.SeriesEpisodesQuery
import com.letsmoe.otter.graphql.type.FillerKind
import com.letsmoe.otter.ui.media.progressFraction

data class SeasonEpisode(
    override val id: String,
    override val seasonNumber: Int,
    override val episodeNumber: Int,
    val name: String?,
    val overview: String?,
    val runtime: Int?,
    val stillPath: String?,
    /** Watch fraction (0..1) for the active profile; null when unwatched. */
    val progress: Float?,
    /** The episode after this one, crossing seasons. */
    val nextEpisodeId: String?,
    /** TMDB's vote average (0..10); 0 when nobody rated it yet. */
    val voteAverage: Double = 0.0,
    /** What a filler list says about the episode (anime); null when none covers it. */
    override val filler: FillerKind? = null,
) : SeriesEpisode {
    val displayName: String
        get() {
            if (name.isNullOrEmpty()) {
                return "Episode $episodeNumber"
            }
            return name
        }

    /** "S1:E3", Netflix's short episode code. */
    val code: String get() = "S$seasonNumber:E$episodeNumber"
}

/** The episode Play starts; [resume] is true when it was started and Play continues it. */
data class UpNext(val episode: SeasonEpisode, val resume: Boolean)

// Watched this far, an episode counts as finished and Play moves on.
internal const val FINISHED_FRACTION = 0.9f

fun SeriesEpisodesQuery.Title.toEpisodes(): List<SeasonEpisode> =
    seasons.flatMap { season ->
        season.episodes.map { episode ->
            var progress: Float? = null
            if (episode.progress != null) {
                progress = progressFraction(episode.progress.progressSeconds, episode.progress.durationSeconds)
            }
            SeasonEpisode(
                id = episode.episodeRef.id,
                seasonNumber = episode.episodeRef.seasonNumber,
                episodeNumber = episode.episodeRef.episodeNumber,
                name = episode.episodeRef.name,
                overview = episode.overview,
                runtime = episode.runtime,
                stillPath = episode.stillPath,
                progress = progress,
                nextEpisodeId = episode.next?.episodeRef?.id,
                voteAverage = episode.voteAverage,
                filler = episode.filler,
            )
        }
    }

/**
 * The furthest started episode (in series order), or the one after it when finished,
 * else the first regular episode. With [skipFiller] filler episodes are passed over.
 */
fun upNextEpisode(episodes: List<SeasonEpisode>, skipFiller: Boolean = false): UpNext? {
    val furthest = episodes.lastOrNull { episode -> episode.progress != null && episode.seasonNumber > 0 }
    if (furthest == null) {
        var first = episodes.firstToWatch(skipFiller)
        if (first == null) {
            first = episodes.firstOrNull()
        }
        if (first == null) {
            return null
        }
        return UpNext(first, resume = false)
    }
    var furthestProgress = 0f
    if (furthest.progress != null) {
        furthestProgress = furthest.progress
    }
    if (furthestProgress < FINISHED_FRACTION) {
        return UpNext(furthest, resume = true)
    }
    val following = followingOf(episodes, furthest, skipFiller)
    if (following != null) {
        return UpNext(following, resume = false)
    }
    return UpNext(furthest, resume = false)
}

// The episode after a finished one: the backend's next, or with [skipFiller] the next that isn't filler.
private fun followingOf(episodes: List<SeasonEpisode>, finished: SeasonEpisode, skipFiller: Boolean): SeasonEpisode? {
    if (skipFiller) {
        return episodes.following(finished.id, skipFiller = true)?.episode
    }
    return episodes.firstOrNull { episode -> episode.id == finished.nextEpisodeId }
}
