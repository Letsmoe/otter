package dev.neoworks.otter.ui.search

import com.apollographql.apollo.api.Optional
import dev.neoworks.otter.graphql.type.TitleFilter

/** The backend filter fields one option sets; unset fields don't narrow the search. */
data class FilterValues(
    val minRating: Double? = null,
    val minRuntime: Int? = null,
    val maxRuntime: Int? = null,
    val minSeasons: Int? = null,
    val maxSeasons: Int? = null,
)

data class FilterOption(val value: String, val label: String, val filter: FilterValues = FilterValues())

/** A group of options with "Any" first, e.g. Rating. */
data class FilterGroup(val key: String, val label: String, val options: List<FilterOption>) {
    val defaultValue: String get() = options.first().value
}

val ratingFilter = FilterGroup(
    key = "rating",
    label = "Rating",
    options = listOf(
        FilterOption("any", "Any"),
        FilterOption("6", "6+", FilterValues(minRating = 6.0)),
        FilterOption("7", "7+", FilterValues(minRating = 7.0)),
        FilterOption("8", "8+", FilterValues(minRating = 8.0)),
    ),
)

val lengthFilter = FilterGroup(
    key = "length",
    label = "Length",
    options = listOf(
        FilterOption("any", "Any"),
        FilterOption("short", "Under 90 min", FilterValues(maxRuntime = 89)),
        FilterOption("medium", "90–120 min", FilterValues(minRuntime = 90, maxRuntime = 120)),
        FilterOption("long", "Over 2 h", FilterValues(minRuntime = 121)),
    ),
)

val seasonsFilter = FilterGroup(
    key = "seasons",
    label = "Seasons",
    options = listOf(
        FilterOption("any", "Any"),
        FilterOption("one", "1", FilterValues(minSeasons = 1, maxSeasons = 1)),
        FilterOption("few", "2–3", FilterValues(minSeasons = 2, maxSeasons = 3)),
        FilterOption("many", "4+", FilterValues(minSeasons = 4)),
    ),
)

/** Combines the chosen option of every group into one backend filter. */
fun combineFilters(groups: List<FilterGroup>, chosen: Map<String, String>): TitleFilter {
    var combined = FilterValues()
    for (group in groups) {
        val option = group.options.firstOrNull { candidate -> candidate.value == chosen[group.key] }
        if (option != null) {
            combined = combined.overriddenBy(option.filter)
        }
    }
    return TitleFilter(
        minRating = Optional.presentIfNotNull(combined.minRating),
        minRuntime = Optional.presentIfNotNull(combined.minRuntime),
        maxRuntime = Optional.presentIfNotNull(combined.maxRuntime),
        minSeasons = Optional.presentIfNotNull(combined.minSeasons),
        maxSeasons = Optional.presentIfNotNull(combined.maxSeasons),
    )
}

private fun FilterValues.overriddenBy(other: FilterValues): FilterValues = FilterValues(
    minRating = pick(other.minRating, minRating),
    minRuntime = pick(other.minRuntime, minRuntime),
    maxRuntime = pick(other.maxRuntime, maxRuntime),
    minSeasons = pick(other.minSeasons, minSeasons),
    maxSeasons = pick(other.maxSeasons, maxSeasons),
)

private fun <T> pick(preferred: T?, fallback: T?): T? {
    if (preferred != null) {
        return preferred
    }
    return fallback
}
