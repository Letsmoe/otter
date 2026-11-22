package com.letsmoe.otter.tv.ui.search

import com.apollographql.apollo.api.Optional
import com.letsmoe.otter.graphql.type.DiscoverFilter
import com.letsmoe.otter.graphql.type.DiscoverSort
import com.letsmoe.otter.graphql.type.MediaType
import com.letsmoe.otter.tv.ui.components.SelectOption

/** What the search's filters are set to; "any" (or an empty genre) doesn't narrow. */
data class SearchFilters(
    val sort: String = SORT_POPULAR,
    val genre: String = "",
    val years: String = ANY,
    val rating: String = ANY,
    val length: String = ANY,
    val seasons: String = ANY,
)

const val ANY = "any"
private const val SORT_POPULAR = "popular"
private const val SORT_TOP_RATED = "top_rated"
private const val SORT_NEWEST = "newest"

val sortOptions = listOf(
    SelectOption(SORT_POPULAR, "Popular"),
    SelectOption(SORT_TOP_RATED, "Top rated"),
    SelectOption(SORT_NEWEST, "Newest"),
)

val yearOptions = listOf(
    SelectOption(ANY, "Any year"),
    SelectOption("2020s", "2020s"),
    SelectOption("2010s", "2010s"),
    SelectOption("2000s", "2000s"),
    SelectOption("1990s", "1990s"),
    SelectOption("1980s", "1980s"),
    SelectOption("older", "Before 1980"),
)

val ratingOptions = listOf(
    SelectOption(ANY, "Any rating"),
    SelectOption("6", "6+"),
    SelectOption("7", "7+"),
    SelectOption("8", "8+"),
)

val lengthOptions = listOf(
    SelectOption(ANY, "Any length"),
    SelectOption("short", "Under 90 min"),
    SelectOption("medium", "90–120 min"),
    SelectOption("long", "Over 2 h"),
)

val seasonOptions = listOf(
    SelectOption(ANY, "Any number"),
    SelectOption("one", "1 season"),
    SelectOption("few", "2–3 seasons"),
    SelectOption("many", "4 or more"),
)

/** The genre names with "Any genre" (an empty value) first. */
fun genreOptions(genres: List<String>): List<SelectOption> {
    return listOf(SelectOption("", "Any genre")) + genres.map { genre -> SelectOption(genre, genre) }
}

/** A closed range; null ends don't bound. */
private class Range(val minimum: Int? = null, val maximum: Int? = null)

private fun yearRange(years: String): Range {
    return when (years) {
        "2020s" -> Range(minimum = 2020)
        "2010s" -> Range(2010, 2019)
        "2000s" -> Range(2000, 2009)
        "1990s" -> Range(1990, 1999)
        "1980s" -> Range(1980, 1989)
        "older" -> Range(maximum = 1979)
        else -> Range()
    }
}

private fun runtimeRange(length: String): Range {
    return when (length) {
        "short" -> Range(maximum = 89)
        "medium" -> Range(90, 120)
        "long" -> Range(minimum = 121)
        else -> Range()
    }
}

private fun seasonRange(seasons: String): Range {
    return when (seasons) {
        "one" -> Range(1, 1)
        "few" -> Range(2, 3)
        "many" -> Range(minimum = 4)
        else -> Range()
    }
}

private fun minimumRating(rating: String): Double? {
    if (rating == ANY) {
        return null
    }
    return rating.toDoubleOrNull()
}

private fun discoverSort(sort: String): DiscoverSort {
    return when (sort) {
        SORT_TOP_RATED -> DiscoverSort.TOP_RATED
        SORT_NEWEST -> DiscoverSort.NEWEST
        else -> DiscoverSort.POPULAR
    }
}

/** The backend filter for [filters]; length only narrows movies, seasons only series. */
fun SearchFilters.toDiscoverFilter(mediaType: MediaType): DiscoverFilter {
    val yearBounds = yearRange(years)
    var runtimeBounds = Range()
    var seasonBounds = Range()
    if (mediaType == MediaType.MOVIE) {
        runtimeBounds = runtimeRange(length)
    } else {
        seasonBounds = seasonRange(seasons)
    }
    var genreName: String? = null
    if (genre.isNotEmpty()) {
        genreName = genre
    }
    return DiscoverFilter(
        genre = Optional.presentIfNotNull(genreName),
        minYear = Optional.presentIfNotNull(yearBounds.minimum),
        maxYear = Optional.presentIfNotNull(yearBounds.maximum),
        minRating = Optional.presentIfNotNull(minimumRating(rating)),
        minRuntime = Optional.presentIfNotNull(runtimeBounds.minimum),
        maxRuntime = Optional.presentIfNotNull(runtimeBounds.maximum),
        minSeasons = Optional.presentIfNotNull(seasonBounds.minimum),
        maxSeasons = Optional.presentIfNotNull(seasonBounds.maximum),
        sort = Optional.present(discoverSort(sort)),
    )
}
