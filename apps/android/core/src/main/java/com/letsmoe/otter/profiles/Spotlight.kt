package com.letsmoe.otter.profiles

import com.apollographql.apollo.api.Optional
import com.letsmoe.otter.api.OtterApi
import com.letsmoe.otter.api.dataOrThrow
import com.letsmoe.otter.api.posterUrl
import com.letsmoe.otter.api.tmdbImageUrl
import com.letsmoe.otter.graphql.SpotlightTitleQuery
import com.letsmoe.otter.graphql.TrendingQuery

/** A trending title shown behind the profile picker. */
data class Spotlight(
    val title: String,
    val posterUrl: String,
    val logoUrl: String?,
    val tagline: String?,
    val backdropUrl: String? = null,
)

private const val SPOTLIGHT_CANDIDATES = 8

/** One of the top trending titles with poster, backdrop, logo and tagline; null offline. */
suspend fun fetchSpotlight(): Spotlight? {
    OtterApi.awaitBackend()
    val trending = try {
        OtterApi.client.query(TrendingQuery(Optional.absent())).execute().dataOrThrow().trending
    } catch (error: Exception) {
        return null
    }
    val candidates = trending.map { item -> item.titleCard }.filter { card -> card.posterPath != null }.take(SPOTLIGHT_CANDIDATES)
    val picked = candidates.randomOrNull() ?: return null
    val fallback = Spotlight(
        title = picked.title,
        posterUrl = posterUrl(picked.posterPath).orEmpty(),
        logoUrl = null,
        tagline = null,
        backdropUrl = tmdbImageUrl(picked.backdropPath, "original"),
    )
    val detail = try {
        OtterApi.client.query(SpotlightTitleQuery(picked.id)).execute().dataOrThrow().title
    } catch (error: Exception) {
        return fallback
    }
    if (detail == null) {
        return fallback
    }
    return fallback.copy(
        posterUrl = tmdbImageUrl(detail.posterPath, "original") ?: fallback.posterUrl,
        logoUrl = tmdbImageUrl(detail.logoPath, "w500"),
        tagline = detail.tagline?.takeIf { tagline -> tagline.isNotBlank() },
    )
}
