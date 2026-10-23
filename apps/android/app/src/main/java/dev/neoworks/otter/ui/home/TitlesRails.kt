package dev.neoworks.otter.ui.home

import dev.neoworks.otter.graphql.type.CatalogTheme
import dev.neoworks.otter.ui.media.RailItem
import dev.neoworks.otter.ui.media.RepeatFilter

enum class RailStyle { REGULAR, LARGE, WIDE, TOP_TEN }

/** One entry of the Home list, in display order. */
sealed interface HomeRow {
    val key: String
}

data class PosterRow(override val key: String, val title: String, val items: List<RailItem>, val style: RailStyle) : HomeRow

data object ActorsRow : HomeRow {
    override val key = "actors"
}

class PageLabels(val plural: String, val newRow: String, val continueTitle: String)

private const val TOP_TEN_SIZE = 10
private const val MIN_RAIL_SIZE = 5
private const val ACTORS_POSITION = 8

/** A candidate row before repeats are removed. */
private class RowSpec(
    val key: String,
    val title: String,
    val items: List<RailItem>,
    val style: RailStyle = RailStyle.REGULAR,
    val minimumItems: Int = MIN_RAIL_SIZE,
    val keepsAllItems: Boolean = false,
)

/**
 * The page's rows in order: personal rows, Top 10, then themed, recommendation and genre rows
 * interleaved so styles and topics vary. Each row loses titles an earlier row shows;
 * rows left with too few titles are dropped.
 */
fun TitlesPageState.toRows(labels: PageLabels): List<HomeRow> {
    val filter = RepeatFilter(featuredIds())
    val rows = mutableListOf<HomeRow>()
    for (spec in orderedSpecs(labels)) {
        val row = spec.toRow(filter)
        if (row != null) {
            rows.add(row)
        }
    }
    return rows
}

private fun TitlesPageState.featuredIds(): List<String> {
    if (featured == null) {
        return emptyList()
    }
    return listOf(featured.item.id)
}

private fun RowSpec.toRow(filter: RepeatFilter): PosterRow? {
    if (keepsAllItems) {
        filter.markSeen(items)
        return PosterRow(key, title, items, style)
    }
    val unique = filter.unseen(items)
    if (unique.size < minimumItems) {
        return null
    }
    return PosterRow(key, title, unique, style)
}

private fun TitlesPageState.orderedSpecs(labels: PageLabels): List<RowSpec> {
    val plural = labels.plural
    val head = listOf(
        RowSpec("continue", labels.continueTitle, continueWatching, minimumItems = 1),
        RowSpec("my-list", "My List", myList, minimumItems = 1),
        RowSpec("top-ten", "Top 10 $plural Today", topTenItems(), RailStyle.TOP_TEN, keepsAllItems = true),
        RowSpec("new", labels.newRow, newAndComingSoon, RailStyle.WIDE),
        RowSpec("recommended", "Recommended $plural", recommended, RailStyle.LARGE),
        RowSpec("popular", "Popular $plural", popular, RailStyle.LARGE),
    )
    val genres = genreRails.map { rail -> RowSpec("genre-${rail.genre}", "${rail.genre} $plural", rail.items) }
    val body = interleave(variedSpecs(plural), genres)
    val tail = RowSpec("watch-again", "Watch It Again", watchAgain, minimumItems = 1)
    return head + body + tail
}

private fun TitlesPageState.topTenItems(): List<RailItem> = trending.take(TOP_TEN_SIZE)

private fun TitlesPageState.themeSpec(theme: CatalogTheme, title: String, style: RailStyle = RailStyle.REGULAR): RowSpec {
    val items = themes[theme]
    if (items == null) {
        return RowSpec("theme-$theme", title, emptyList(), style)
    }
    return RowSpec("theme-$theme", title, items, style)
}

// Themed, "because you watched" and rating rows, spaced so the genre rows fall in between.
private fun TitlesPageState.variedSpecs(plural: String): List<RowSpec> {
    val because = becauseRows.mapIndexed { index, rail -> RowSpec("because-$index", rail.genre, rail.items) }
    val moreWithSpec = RowSpec("more-with", moreWithTitle(), moreWith?.items.orEmpty())
    return listOfNotNull(
        RowSpec("trending", "Trending $plural", trending, RailStyle.LARGE),
        themeSpec(CatalogTheme.HIDDEN_GEMS, "Hidden Gems"),
        because.getOrNull(0),
        RowSpec("top-rated", "Critically Acclaimed $plural", topRated),
        themeSpec(CatalogTheme.BLOCKBUSTERS, "Blockbusters", RailStyle.LARGE),
        themeSpec(CatalogTheme.BINGEWORTHY, "Bingeworthy $plural", RailStyle.LARGE),
        moreWithSpec,
        because.getOrNull(1),
        themeSpec(CatalogTheme.FEEL_GOOD, "Feel-Good Picks", RailStyle.WIDE),
        because.getOrNull(2),
    )
}

private fun TitlesPageState.moreWithTitle(): String {
    if (moreWith == null) {
        return ""
    }
    return "More with ${moreWith.personName}"
}

private fun <T> interleave(first: List<T>, second: List<T>): List<T> {
    val merged = mutableListOf<T>()
    for (index in 0 until maxOf(first.size, second.size)) {
        first.getOrNull(index)?.let { item -> merged.add(item) }
        second.getOrNull(index)?.let { item -> merged.add(item) }
    }
    return merged
}

/** Puts the actor row in the middle of the page, between poster rows. */
fun List<HomeRow>.withActorsRow(hasActors: Boolean): List<HomeRow> {
    if (!hasActors) {
        return this
    }
    val position = minOf(size, ACTORS_POSITION)
    return take(position) + ActorsRow + drop(position)
}
