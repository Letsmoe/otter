package com.letsmoe.otter.ui.watchorder

import com.letsmoe.otter.api.posterUrl
import com.letsmoe.otter.api.tmdbImageUrl
import com.letsmoe.otter.graphql.fragment.CollectionGuide
import com.letsmoe.otter.graphql.type.WatchStatus

enum class PartStatus { WATCHED, IN_PROGRESS, NOT_YET }

/** One film of a franchise, in watch order. */
data class GuidePart(
    val number: Int,
    val titleId: String,
    val title: String,
    val year: Int?,
    val posterUrl: String?,
    val status: PartStatus,
    /** Watched share (0..1) of a film in progress. */
    val progress: Float,
    /** False for films announced but not out yet. */
    val released: Boolean,
)

/** A franchise (TMDB collection) with the profile's progress through its films. */
data class WatchOrderGuide(
    val id: String,
    /** The franchise as TMDB names it, e.g. "The Matrix Collection". */
    val name: String,
    val overview: String?,
    val backdropUrl: String?,
    val posterUrl: String?,
    val parts: List<GuidePart>,
    val nextNumber: Int?,
) {
    val watchedCount: Int get() = parts.count { part -> part.status == PartStatus.WATCHED }

    /** The film to continue with; null when everything out is watched. */
    val next: GuidePart? get() = parts.firstOrNull { part -> part.number == nextNumber }

    val isStarted: Boolean get() = parts.any { part -> part.status != PartStatus.NOT_YET }

    /** The franchise's name as artwork headline, without TMDB's "Collection" suffix. */
    val headline: String get() = franchiseHeadline(name)

    /** "2001–2011", or the single year; null when no film has a date. */
    val yearSpan: String? get() = yearSpanOf(parts.mapNotNull { part -> part.year })
}

private val collectionSuffixes = listOf(" Collection", " Kollektion", " Filmreihe", " Filmsammlung")

/** Drops the generic word TMDB appends to franchise names ("The Matrix Collection" is "The Matrix"). */
fun franchiseHeadline(name: String): String {
    val trimmed = name.trim()
    for (suffix in collectionSuffixes) {
        if (trimmed.endsWith(suffix, ignoreCase = true) && trimmed.length > suffix.length) {
            return trimmed.dropLast(suffix.length).trimEnd(' ', '-')
        }
    }
    return trimmed
}

fun yearSpanOf(years: List<Int>): String? {
    if (years.isEmpty()) {
        return null
    }
    val first = years.min()
    val last = years.max()
    if (first == last) {
        return first.toString()
    }
    return "$first–$last"
}

fun CollectionGuide.toGuide(): WatchOrderGuide = WatchOrderGuide(
    id = id,
    name = name,
    overview = overview,
    backdropUrl = tmdbImageUrl(backdropPath, "w1280"),
    posterUrl = posterUrl(posterPath),
    parts = parts.map { part -> part.toGuidePart() },
    nextNumber = next?.number,
)

private fun CollectionGuide.Part.toGuidePart(): GuidePart = GuidePart(
    number = number,
    titleId = title.id,
    title = title.title,
    year = title.year,
    posterUrl = posterUrl(title.posterPath),
    status = status.toPartStatus(),
    progress = progress.toFloat(),
    released = released,
)

private fun WatchStatus.toPartStatus(): PartStatus = when (this) {
    WatchStatus.WATCHED -> PartStatus.WATCHED
    WatchStatus.IN_PROGRESS -> PartStatus.IN_PROGRESS
    else -> PartStatus.NOT_YET
}
