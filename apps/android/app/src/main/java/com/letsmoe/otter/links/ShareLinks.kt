package com.letsmoe.otter.links

import java.net.URI
import java.text.Normalizer

/** Shared links open in Otter when installed and on this site's fallback page otherwise. */
const val SHARE_HOST = "otter.letsmoe.com"
private const val SHARE_ORIGIN = "https://$SHARE_HOST"
private const val MAX_SLUG_LENGTH = 60

private val episodePattern = Regex("^s(\\d+)e(\\d+)$")
private val leadingNumber = Regex("^(\\d+)")

/** What a shared link points at; ids are TMDB's, the same in every Otter install. */
sealed interface SharedTarget {
    data class Movie(val tmdbId: Int) : SharedTarget
    data class Series(val tmdbId: Int) : SharedTarget
    data class Episode(val seriesTmdbId: Int, val seasonNumber: Int, val episodeNumber: Int) : SharedTarget
}

/** https://otter.letsmoe.com/movie/603-the-matrix */
fun movieLink(tmdbId: Int, title: String): String {
    return "$SHARE_ORIGIN/movie/${idWithSlug(tmdbId, title)}"
}

/** https://otter.letsmoe.com/tv/1396-breaking-bad */
fun seriesLink(tmdbId: Int, title: String): String {
    return "$SHARE_ORIGIN/tv/${idWithSlug(tmdbId, title)}"
}

/** https://otter.letsmoe.com/tv/1396-breaking-bad/s1e1 */
fun episodeLink(seriesTmdbId: Int, seriesTitle: String, seasonNumber: Int, episodeNumber: Int): String {
    return "${seriesLink(seriesTmdbId, seriesTitle)}/s${seasonNumber}e$episodeNumber"
}

/** The TMDB id in an API id: "movie:603" → 603. */
fun tmdbIdOf(id: String): Int {
    return id.substringAfter(':').toIntOrNull() ?: 0
}

/** The message sent along with a link, e.g. "The Matrix (1999) on Otter: https://…". */
fun shareText(label: String, link: String): String {
    return "$label on Otter: $link"
}

/** Reads a link made by the functions above; null for anything else. */
fun parseShareLink(link: String): SharedTarget? {
    val uri = try {
        URI(link)
    } catch (error: Exception) {
        return null
    }
    if (uri.host != SHARE_HOST) {
        return null
    }
    val segments = uri.path.orEmpty().split('/').filter { segment -> segment.isNotEmpty() }
    if (segments.size < 2) {
        return null
    }
    val tmdbId = leadingId(segments[1]) ?: return null
    return when (segments[0]) {
        "movie" -> SharedTarget.Movie(tmdbId)
        "tv" -> seriesTarget(tmdbId, segments.getOrNull(2))
        else -> null
    }
}

private fun seriesTarget(tmdbId: Int, episodeSegment: String?): SharedTarget {
    val match = episodeSegment?.let { segment -> episodePattern.find(segment.lowercase()) }
    if (match == null) {
        return SharedTarget.Series(tmdbId)
    }
    return SharedTarget.Episode(tmdbId, match.groupValues[1].toInt(), match.groupValues[2].toInt())
}

private fun leadingId(segment: String): Int? {
    val match = leadingNumber.find(segment) ?: return null
    return match.groupValues[1].toIntOrNull()
}

private fun idWithSlug(tmdbId: Int, title: String): String {
    val slug = slugOf(title)
    if (slug.isEmpty()) {
        return tmdbId.toString()
    }
    return "$tmdbId-$slug"
}

/** "Amélie: Le Fabuleux" → "amelie-le-fabuleux"; only for reading, the id alone decides. */
fun slugOf(title: String): String {
    val withoutAccents = Normalizer.normalize(title, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")
    val dashed = withoutAccents.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')
    return dashed.take(MAX_SLUG_LENGTH).trimEnd('-')
}
