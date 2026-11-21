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

/** What the hero shows beyond a card's fields: TMDB's logo artwork of the name and the description. */
data class TitleExtras(val logoUrl: String?, val overview: String?)

private val NO_EXTRAS = TitleExtras(logoUrl = null, overview = null)

/** A title's logo and description by title id; looked up once per title and kept while the app runs. */
object TitleLogos {

    private val extrasById = mutableMapOf<String, TitleExtras>()
    // The logo and the description of one title are asked at once; one request answers both.
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
        return TitleExtras(logoUrl = tmdbImageUrl(title.logoPath, "w500"), overview = nonBlank(title.overview))
    }
}

private fun nonBlank(text: String?): String? {
    if (text.isNullOrBlank()) {
        return null
    }
    return text
}

/** The title's logo URL once known; null while loading and for titles without one. */
@Composable
fun rememberTitleLogo(titleId: String?): State<String?> {
    return produceState<String?>(null, titleId) {
        if (titleId == null) {
            value = null
            return@produceState
        }
        value = TitleLogos.extras(titleId).logoUrl
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
        value = TitleLogos.extras(titleId).overview
    }
}
