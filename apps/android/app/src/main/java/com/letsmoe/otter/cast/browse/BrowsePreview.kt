package com.letsmoe.otter.cast.browse

import com.letsmoe.otter.graphql.type.MediaType
import com.letsmoe.otter.ui.title.TitleDetail
import com.letsmoe.otter.ui.title.movieFacts
import com.letsmoe.otter.ui.title.seriesFacts

private const val PAGE_BACKDROP_SIZE = "/w1280/"
// The TV's picture is 1080 lines tall: the page's backdrop is too small for it.
private const val TELEVISION_BACKDROP_SIZE = "/original/"
private const val FACT_SEPARATOR = " · "

/** What the TV shows of the title the phone is looking at. */
data class BrowsePreview(
    val titleId: String,
    val title: String,
    val facts: List<String>,
    val overview: String?,
    val backdropUrl: String?,
) {
    val factsLine: String
        get() = facts.joinToString(FACT_SEPARATOR)
}

fun browsePreviewOf(detail: TitleDetail): BrowsePreview {
    var facts = movieFacts(detail)
    if (detail.mediaType == MediaType.SERIES) {
        facts = seriesFacts(detail)
    }
    return BrowsePreview(detail.id, detail.title, withAgeRating(facts, detail.ageRating), detail.overview, televisionBackdropUrl(detail.bannerUrl))
}

/** The age rating ("FSK 12") leads the facts, as it does on a cinema poster; nothing is added while it is unknown. */
fun withAgeRating(facts: List<String>, ageRating: String?): List<String> {
    if (ageRating.isNullOrEmpty()) {
        return facts
    }
    return listOf(ageRating) + facts
}

/** The same image as the title page's banner, in the size a full-screen TV picture needs. */
fun televisionBackdropUrl(bannerUrl: String?): String? {
    if (bannerUrl == null) {
        return null
    }
    return bannerUrl.replace(PAGE_BACKDROP_SIZE, TELEVISION_BACKDROP_SIZE)
}
