package com.letsmoe.otter.tv.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import com.letsmoe.otter.api.OtterApi
import com.letsmoe.otter.api.dataOrThrow
import com.letsmoe.otter.api.tmdbImageUrl
import com.letsmoe.otter.graphql.SpotlightTitleQuery
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
)

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
        )
    }
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

/** The title's description once known; null while loading and for titles without one. */
@Composable
fun rememberTitleOverview(titleId: String?): State<String?> {
    return produceState<String?>(null, titleId) {
        if (titleId == null) {
            value = null
            return@produceState
        }
        value = TitleExtrasCache.extras(titleId).overview
    }
}
