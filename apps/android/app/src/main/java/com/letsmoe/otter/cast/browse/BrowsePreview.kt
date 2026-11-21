package com.letsmoe.otter.cast.browse

import com.letsmoe.otter.api.tmdbImageUrl
import com.letsmoe.otter.graphql.type.MediaType
import com.letsmoe.otter.ui.title.SeasonEpisode
import com.letsmoe.otter.ui.title.TitleDetail
import com.letsmoe.otter.ui.title.formatRuntime
import com.letsmoe.otter.ui.title.movieFacts
import com.letsmoe.otter.ui.title.seriesFacts

private const val PAGE_BACKDROP_SIZE = "/w1280/"
// The TV's picture is 1080 lines tall: the page's backdrop is too small for it.
private const val TELEVISION_BACKDROP_SIZE = "/original/"
private const val TELEVISION_STILL_SIZE = "original"
private const val FACT_SEPARATOR = " · "

/**
 * What the TV shows of the title the phone is looking at. With [fullOverview] the TV shows all
 * of [overview] (the phone opened the description), else its first lines.
 */
data class BrowsePreview(
    val titleId: String,
    val title: String,
    val facts: List<String>,
    val overview: String?,
    val backdropUrl: String?,
    val fullOverview: Boolean = false,
) {
    val factsLine: String
        get() = facts.joinToString(FACT_SEPARATOR)
}

/**
 * An episode whose description the phone opened: its still, its name, the series and episode
 * number, and its whole description. Without a still the series' backdrop stays.
 */
fun episodePreviewOf(detail: TitleDetail, episode: SeasonEpisode): BrowsePreview {
    val facts = listOfNotNull(detail.title, "Season ${episode.seasonNumber} · Episode ${episode.episodeNumber}", formatRuntime(episode.runtime))
    var backdropUrl = tmdbImageUrl(episode.stillPath, TELEVISION_STILL_SIZE)
    if (backdropUrl == null) {
        backdropUrl = detail.bannerUrl
    }
    return BrowsePreview(detail.id, episode.displayName, facts, episode.overview, backdropUrl, fullOverview = true)
}

fun browsePreviewOf(detail: TitleDetail): BrowsePreview {
    var facts = movieFacts(detail)
    if (detail.mediaType == MediaType.SERIES) {
        facts = seriesFacts(detail)
    }
    // The page's own banner, already in the image cache: a larger one would be downloaded first, for seconds.
    return BrowsePreview(detail.id, detail.title, facts, detail.overview, detail.bannerUrl)
}

/** The same image as the title page's banner, in the size a full-screen TV picture needs. */
fun televisionBackdropUrl(bannerUrl: String?): String? {
    if (bannerUrl == null) {
        return null
    }
    return bannerUrl.replace(PAGE_BACKDROP_SIZE, TELEVISION_BACKDROP_SIZE)
}
