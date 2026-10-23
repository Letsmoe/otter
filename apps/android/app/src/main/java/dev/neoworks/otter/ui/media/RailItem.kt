package dev.neoworks.otter.ui.media

import dev.neoworks.otter.api.backdropUrl
import dev.neoworks.otter.api.posterUrl
import dev.neoworks.otter.graphql.fragment.EpisodeRef
import dev.neoworks.otter.graphql.fragment.TitleCard
import dev.neoworks.otter.graphql.type.MediaType

/** What a poster card shows; built from whichever query selected the title. */
data class RailItem(
    val id: String,
    val mediaType: MediaType,
    val title: String,
    val year: Int?,
    val posterUrl: String?,
    val backdropUrl: String? = null,
    val subtitle: String? = null,
    /** Watch fraction (0..1) for continue watching and history. */
    val progress: Float? = null,
    /** Short label over the poster, e.g. a release date for upcoming titles. */
    val badge: String? = null,
    /** Set when the card opens one episode of a series. */
    val episodeId: String? = null,
    val seasonNumber: Int? = null,
    val watchedAt: String? = null,
    /** Netflix-style tag under the poster, e.g. "Recently Added". */
    val label: String? = null,
    /** 1-based place in a Top 10 row. */
    val rank: Int? = null,
)

fun TitleCard.toRailItem(): RailItem {
    var yearLabel: String? = null
    if (year != null) {
        yearLabel = year.toString()
    }
    return RailItem(
        id = id,
        mediaType = mediaType,
        title = title,
        year = year,
        posterUrl = posterUrl(posterPath),
        backdropUrl = backdropUrl(backdropPath),
        subtitle = yearLabel,
        label = recentlyAddedLabel(releaseDate),
    )
}

fun RailItem.withEpisode(episode: EpisodeRef): RailItem = copy(
    subtitle = episodeLabel(episode),
    episodeId = episode.id,
    seasonNumber = episode.seasonNumber,
)

/** "S1E3 · Name". */
fun episodeLabel(episode: EpisodeRef): String {
    val code = "S${episode.seasonNumber}E${episode.episodeNumber}"
    val name = episode.name
    if (name.isNullOrEmpty()) {
        return code
    }
    return "$code · $name"
}

fun progressFraction(progressSeconds: Double, durationSeconds: Double): Float? {
    if (durationSeconds <= 0) {
        return null
    }
    return (progressSeconds / durationSeconds).toFloat().coerceIn(0f, 1f)
}

fun List<RailItem>.onlyKind(mediaType: MediaType): List<RailItem> = filter { item -> item.mediaType == mediaType }

/**
 * Drops titles an earlier rail (or `alreadyShown`) has, so every title appears once per page.
 * TMDB sometimes lists one film under several ids, so a matching name and year counts too.
 */
fun withoutRepeats(rails: List<List<RailItem>>, alreadyShown: List<String> = emptyList()): List<List<RailItem>> {
    val filter = RepeatFilter(alreadyShown)
    return rails.map { items -> filter.unseen(items) }
}

/** Remembers the titles shown so far; rails that must stay whole can still mark theirs as seen. */
class RepeatFilter(alreadyShown: List<String> = emptyList()) {
    private val seen = alreadyShown.toMutableSet()

    fun unseen(items: List<RailItem>): List<RailItem> =
        items.filter { item -> seen.add(item.id) && seen.add(nameKey(item)) }

    fun markSeen(items: List<RailItem>) {
        items.forEach { item ->
            seen.add(item.id)
            seen.add(nameKey(item))
        }
    }
}

private fun nameKey(item: RailItem): String {
    var subtitle = ""
    if (item.subtitle != null) {
        subtitle = item.subtitle
    }
    return "${item.mediaType}|${item.title.lowercase()}|$subtitle"
}

private fun releaseYear(item: RailItem): Int {
    if (item.year == null) {
        return 0
    }
    return item.year
}

enum class ListOrder(val label: String) {
    ADDED("Recently Added"),
    TITLE("A–Z"),
    RELEASE("Release Date"),
}

/** 'Added' keeps the given (newest saved first) order. */
fun List<RailItem>.sortedBy(order: ListOrder): List<RailItem> = when (order) {
    ListOrder.ADDED -> this
    ListOrder.TITLE -> sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { item -> item.title })
    ListOrder.RELEASE -> sortedByDescending { item -> releaseYear(item) }
}
