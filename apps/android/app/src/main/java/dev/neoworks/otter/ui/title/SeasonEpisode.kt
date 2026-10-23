package dev.neoworks.otter.ui.title

import dev.neoworks.otter.graphql.SeriesEpisodesQuery
import dev.neoworks.otter.ui.media.progressFraction

data class SeasonEpisode(
    val id: String,
    val seasonNumber: Int,
    val episodeNumber: Int,
    val name: String?,
    val overview: String?,
    val runtime: Int?,
    val stillPath: String?,
    /** Watch fraction (0..1) for the active profile; null when unwatched. */
    val progress: Float?,
    /** The episode after this one, crossing seasons. */
    val nextEpisodeId: String?,
) {
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
private const val FINISHED_FRACTION = 0.9f

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
            )
        }
    }

/**
 * The furthest started episode (in series order), or the one after it when finished,
 * else the first regular episode.
 */
fun upNextEpisode(episodes: List<SeasonEpisode>): UpNext? {
    val furthest = episodes.lastOrNull { episode -> episode.progress != null && episode.seasonNumber > 0 }
    if (furthest == null) {
        var first = episodes.firstOrNull { episode -> episode.seasonNumber > 0 }
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
    val following = episodes.firstOrNull { episode -> episode.id == furthest.nextEpisodeId }
    if (following != null) {
        return UpNext(following, resume = false)
    }
    return UpNext(furthest, resume = false)
}
