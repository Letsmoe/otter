package dev.neoworks.otter.ui.player

import dev.neoworks.otter.api.OtterApi
import dev.neoworks.otter.api.dataOrThrow
import dev.neoworks.otter.api.tmdbImageUrl
import dev.neoworks.otter.graphql.PlayerEpisodeQuery
import dev.neoworks.otter.graphql.PlayerMovieQuery
import dev.neoworks.otter.graphql.PlayerRatingQuery
import dev.neoworks.otter.graphql.PlayerSeriesEpisodesQuery
import dev.neoworks.otter.graphql.fragment.PlayerProgressFields
import kotlinx.coroutines.CancellationException

/** What the player shows about the playing movie or episode. */
data class PlayerMedia(
    val title: String,
    /** App path that plays the title; read by the web apps in Watch Together. */
    val route: String,
    val episodes: List<PlayerEpisode>,
    val nextEpisodeId: String?,
    /** Intro, recap and the like, from TheIntroDB; empty for movies and untimed episodes. */
    val skipSegments: List<SkipSegment> = emptyList(),
)

/** A skippable part; endSeconds is null when it runs to the end. */
data class SkipSegment(val kind: String, val startSeconds: Double, val endSeconds: Double?)

// Resume only from this far in; shorter starts are likely a misclick.
private const val RESUME_MIN_SECONDS = 30.0
private const val STILL_SIZE = "w780"

fun isEpisodeId(mediaId: String): Boolean = mediaId.startsWith("episode:")

/** Loads a movie or an episode with its series' episodes; null when the backend can't tell. */
suspend fun loadPlayerMedia(mediaId: String, profileId: String): PlayerMedia? {
    return try {
        if (isEpisodeId(mediaId)) {
            loadEpisodeMedia(mediaId, profileId)
        } else {
            loadMovieMedia(mediaId, profileId)
        }
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (error: Exception) {
        null
    }
}

private suspend fun loadMovieMedia(mediaId: String, profileId: String): PlayerMedia? {
    val data = OtterApi.client.query(PlayerMovieQuery(mediaId, profileId)).execute().dataOrThrow()
    val title = data.title ?: return null
    return PlayerMedia(title.title, "/movies/$mediaId", emptyList(), null)
}

private suspend fun loadEpisodeMedia(mediaId: String, profileId: String): PlayerMedia? {
    val data = OtterApi.client.query(PlayerEpisodeQuery(mediaId, profileId)).execute().dataOrThrow()
    val episode = data.episode ?: return null
    val seriesId = episode.series.id
    val title = "${episode.series.title} · ${episodeCode(episode.seasonNumber, episode.episodeNumber)} ${episodeName(episode.name, episode.episodeNumber)}"
    val episodes = loadSeriesEpisodes(seriesId, profileId)
    val nextId = episode.next?.id
    val nextKnown = episodes.any { candidate -> candidate.id == nextId }
    val skipSegments = episode.skipSegments.map { segment -> SkipSegment(segment.kind, segment.startSeconds, segment.endSeconds) }
    return PlayerMedia(title, "/series/$seriesId?episode=$mediaId", episodes, nextId.takeIf { nextKnown }, skipSegments)
}

private suspend fun loadSeriesEpisodes(seriesId: String, profileId: String): List<PlayerEpisode> {
    val data = OtterApi.client.query(PlayerSeriesEpisodesQuery(seriesId, profileId)).execute().dataOrThrow()
    val seasons = data.title?.seasons.orEmpty()
    return seasons.flatMap { season ->
        season.episodes.map { episode ->
            PlayerEpisode(
                id = episode.id,
                seasonNumber = episode.seasonNumber,
                episodeNumber = episode.episodeNumber,
                name = episode.name,
                overview = episode.overview,
                stillUrl = tmdbImageUrl(episode.stillPath, STILL_SIZE),
                progress = watchedFraction(episode.progress?.playerProgressFields),
            )
        }
    }
}

private fun watchedFraction(progress: PlayerProgressFields?): Float? {
    if (progress == null || progress.durationSeconds <= 0) {
        return null
    }
    return (progress.progressSeconds / progress.durationSeconds).toFloat().coerceIn(0f, 1f)
}

fun episodeCode(seasonNumber: Int, episodeNumber: Int): String = "S$seasonNumber:E$episodeNumber"

fun episodeName(name: String?, episodeNumber: Int): String {
    if (name.isNullOrEmpty()) {
        return "Episode $episodeNumber"
    }
    return name
}

/** Where the profile stopped watching, or 0 when it hasn't started or finished. */
suspend fun fetchResumePosition(mediaId: String, profileId: String): Double {
    val progress = try {
        fetchProgress(mediaId, profileId)
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (error: Exception) {
        null
    }
    if (progress == null || progress.completed || progress.progressSeconds < RESUME_MIN_SECONDS) {
        return 0.0
    }
    return progress.progressSeconds
}

private suspend fun fetchProgress(mediaId: String, profileId: String): PlayerProgressFields? {
    if (isEpisodeId(mediaId)) {
        val data = OtterApi.client.query(PlayerEpisodeQuery(mediaId, profileId)).execute().dataOrThrow()
        return data.episode?.progress?.playerProgressFields
    }
    val data = OtterApi.client.query(PlayerMovieQuery(mediaId, profileId)).execute().dataOrThrow()
    return data.title?.progress?.playerProgressFields
}

/** The profile's rating of a title or episode, or null when unrated. */
suspend fun fetchUserRating(mediaId: String, profileId: String): Double? {
    return try {
        OtterApi.client.query(PlayerRatingQuery(mediaId, profileId)).execute().dataOrThrow().title?.rating
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (error: Exception) {
        null
    }
}
