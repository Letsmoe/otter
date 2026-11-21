package com.letsmoe.otter.tv.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import com.letsmoe.otter.api.OtterApi
import com.letsmoe.otter.api.dataOrThrow
import com.letsmoe.otter.api.tmdbImageUrl
import com.letsmoe.otter.graphql.SpotlightTitleQuery

/** Title logos (TMDB's artwork of the name) by title id; looked up once per title and kept while the app runs. */
object TitleLogos {

    // A title without a logo is stored as null, so it isn't asked again.
    private val logoUrls = mutableMapOf<String, String?>()

    suspend fun logoUrl(titleId: String): String? {
        if (logoUrls.containsKey(titleId)) {
            return logoUrls[titleId]
        }
        val logo = fetchLogo(titleId)
        logoUrls[titleId] = logo
        return logo
    }

    private suspend fun fetchLogo(titleId: String): String? {
        return try {
            val title = OtterApi.client.query(SpotlightTitleQuery(titleId)).execute().dataOrThrow().title
            tmdbImageUrl(title?.logoPath, "w500")
        } catch (error: Exception) {
            null
        }
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
        value = TitleLogos.logoUrl(titleId)
    }
}
