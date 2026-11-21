package com.letsmoe.otter.ui.title

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import kotlin.math.roundToInt

/** Votes at or below this get the coolest colour, votes at or above [HIGHEST_VOTE] the brightest. */
const val LOWEST_VOTE = 6.0
const val HIGHEST_VOTE = 9.5

// Viridis from its dark purple to its yellow (the first stop is lifted off black so it still
// shows on the dark UI). Viridis keeps its lightness rising steadily and is distinguishable with
// the common kinds of colour blindness, so rating never depends on telling hues apart.
private val heatStops = listOf(
    Color(0xFF4B3A8C),
    Color(0xFF3B528B),
    Color(0xFF21908D),
    Color(0xFF5DC863),
    Color(0xFFFDE725),
)

/** The tile colour for an episode rated [vote] by TMDB users. */
fun heatColor(vote: Double): Color {
    val position = ((vote - LOWEST_VOTE) / (HIGHEST_VOTE - LOWEST_VOTE)).coerceIn(0.0, 1.0)
    val scaled = position * (heatStops.size - 1)
    val lower = scaled.toInt().coerceAtMost(heatStops.size - 2)
    return lerp(heatStops[lower], heatStops[lower + 1], (scaled - lower).toFloat())
}

/** The colour of the scale at [fraction] (0 to 1) of its length, for the legend. */
fun heatLegendColor(fraction: Float): Color = heatColor(LOWEST_VOTE + fraction * (HIGHEST_VOTE - LOWEST_VOTE))

/** Dark or light, whichever reads on [background]: for the mark on watched tiles. */
fun markColorOn(background: Color): Color {
    if (background.luminance() > 0.3f) {
        return Color(0xFF111114)
    }
    return Color.White
}

/** One season's episodes in order, one row of the heatmap. */
data class HeatmapSeason(val seasonNumber: Int, val episodes: List<SeasonEpisode>)

/** The regular seasons (no specials) in order, each with its episodes in order. */
fun heatmapSeasons(episodes: List<SeasonEpisode>): List<HeatmapSeason> {
    return episodes
        .filter { episode -> episode.seasonNumber > 0 }
        .groupBy { episode -> episode.seasonNumber }
        .toSortedMap()
        .map { (seasonNumber, seasonEpisodes) -> HeatmapSeason(seasonNumber, seasonEpisodes.sortedBy { episode -> episode.episodeNumber }) }
}

/** Whether the profile watched the episode to its end (the rest is credits). */
fun isWatched(episode: SeasonEpisode): Boolean {
    val progress = episode.progress ?: return false
    return progress >= FINISHED_FRACTION
}

fun hasRating(episode: SeasonEpisode): Boolean = episode.voteAverage > 0

private const val MINIMUM_RATED_EPISODES = 3

/** A heatmap tells something once a few episodes are rated; a miniseries with one rating does not. */
fun worthShowing(seasons: List<HeatmapSeason>): Boolean {
    return seasons.sumOf { season -> season.episodes.count { episode -> hasRating(episode) } } >= MINIMUM_RATED_EPISODES
}

/** "S2:E3 · The Long Way Home". */
fun heatmapEpisodeTitle(episode: SeasonEpisode): String = "${episode.code} · ${episode.displayName}"

/** What a screen reader says of a tile: "S1:E1 · Pilot, ★ 8.5, watched". */
fun heatmapTileDescription(episode: SeasonEpisode): String {
    var description = heatmapEpisodeTitle(episode) + ", " + heatmapRatingText(episode)
    if (isWatched(episode)) {
        description += ", watched"
    }
    return description
}

/** "★ 8.7", or "Not rated yet". */
fun heatmapRatingText(episode: SeasonEpisode): String {
    if (!hasRating(episode)) {
        return "Not rated yet"
    }
    return "★ " + ((episode.voteAverage * 10).roundToInt() / 10.0).toString()
}
