package com.letsmoe.otter.tv.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import com.letsmoe.otter.api.OtterApi
import com.letsmoe.otter.api.dataOrThrow
import com.letsmoe.otter.api.tmdbImageUrl
import com.letsmoe.otter.graphql.SpotlightTitleQuery
import com.letsmoe.otter.graphql.type.MediaType
import java.util.Locale
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * What the hero and card previews show beyond a card's fields: TMDB's logo artwork of the name,
 * the description, the trailer's YouTube key and the facts line's genres, runtime and seasons.
 */
data class TitleExtras(
    val logoUrl: String? = null,
    val overview: String? = null,
    val trailerKey: String? = null,
    val genres: List<String> = emptyList(),
    val runtimeMinutes: Int? = null,
    val seasonCount: Int = 0,
    val voteAverage: Double = 0.0,
    val starring: List<String> = emptyList(),
    val directors: List<String> = emptyList(),
)

private const val STARRING_COUNT = 3

/** "Cillian Murphy, Emily Blunt, Matt Damon. Directed by Christopher Nolan."; null without credits. */
fun TitleExtras.creditLine(): String? {
    val sentences = mutableListOf<String>()
    if (starring.isNotEmpty()) {
        sentences.add(starring.joinToString(", ") + ".")
    }
    if (directors.isNotEmpty()) {
        sentences.add("Directed by " + directors.joinToString(", ") + ".")
    }
    if (sentences.isEmpty()) {
        return null
    }
    return sentences.joinToString(" ")
}

/** "1h 57m" for a movie, "3 Seasons" for a series; null while unknown. */
fun TitleExtras.lengthLabel(mediaType: MediaType): String? {
    if (mediaType == MediaType.SERIES) {
        return seasonsLabel(seasonCount)
    }
    return runtimeLabel(runtimeMinutes)
}

/** "★ 7.9"; null for titles nobody rated yet. */
fun TitleExtras.ratingLabel(): String? {
    if (voteAverage <= 0.0) {
        return null
    }
    return "★ " + String.format(Locale.US, "%.1f", voteAverage)
}

private fun seasonsLabel(seasonCount: Int): String? {
    if (seasonCount <= 0) {
        return null
    }
    if (seasonCount == 1) {
        return "1 Season"
    }
    return "$seasonCount Seasons"
}

private fun runtimeLabel(minutes: Int?): String? {
    if (minutes == null || minutes <= 0) {
        return null
    }
    val hours = minutes / 60
    val rest = minutes % 60
    if (hours == 0) {
        return "${rest}m"
    }
    return "${hours}h ${rest}m"
}

private val NO_EXTRAS = TitleExtras()

/** A title's extras by title id; looked up once per title and kept while the app runs. */
object TitleExtrasCache {

    private val extrasById = mutableMapOf<String, TitleExtras>()
    // The logo, description and preview of one title are asked at once; one request answers all.
    private val lookup = Mutex()

    suspend fun extras(titleId: String): TitleExtras {
        return lookup.withLock {
            val known = extrasById[titleId]
            if (known != null) {
                return@withLock known
            }
            val fetched = fetchExtras(titleId)
            extrasById[titleId] = fetched
            fetched
        }
    }

    private suspend fun fetchExtras(titleId: String): TitleExtras {
        val title = try {
            OtterApi.client.query(SpotlightTitleQuery(titleId)).execute().dataOrThrow().title
        } catch (error: Exception) {
            return NO_EXTRAS
        }
        if (title == null) {
            return NO_EXTRAS
        }
        return TitleExtras(
            logoUrl = tmdbImageUrl(title.logoPath, "w500"),
            overview = nonBlank(title.overview),
            trailerKey = nonBlank(title.trailerKey),
            genres = title.genres,
            runtimeMinutes = title.runtime,
            seasonCount = title.seasonCount,
            voteAverage = title.voteAverage,
            starring = starringOf(title),
            directors = directorsOf(title),
        )
    }
}

private fun starringOf(title: SpotlightTitleQuery.Title): List<String> {
    return title.credits
        .filter { credit -> credit.role == "cast" }
        .sortedBy { credit -> credit.order }
        .take(STARRING_COUNT)
        .map { credit -> credit.person.name }
}

private fun directorsOf(title: SpotlightTitleQuery.Title): List<String> {
    return title.credits
        .filter { credit -> credit.role == "crew" && credit.job == "Director" }
        .map { credit -> credit.person.name }
        .distinct()
}

private fun nonBlank(text: String?): String? {
    if (text.isNullOrBlank()) {
        return null
    }
    return text
}

/** The title's extras once known; null while loading. */
@Composable
fun rememberTitleExtras(titleId: String?): State<TitleExtras?> {
    return produceState<TitleExtras?>(null, titleId) {
        if (titleId == null) {
            value = null
            return@produceState
        }
        value = TitleExtrasCache.extras(titleId)
    }
}

/** The title's logo URL once known; null while loading and for titles without one. */
@Composable
fun rememberTitleLogo(titleId: String?): State<String?> {
    return produceState<String?>(null, titleId) {
        if (titleId == null) {
            value = null
            return@produceState
        }
        value = TitleExtrasCache.extras(titleId).logoUrl
    }
}
