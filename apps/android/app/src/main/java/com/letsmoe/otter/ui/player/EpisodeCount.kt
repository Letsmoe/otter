package com.letsmoe.otter.ui.player

/**
 * "Episode 3 of 9": where the episode stands among the episodes of its season. Null when the
 * season has only this one or the episode isn't in the list.
 */
fun episodeCountLabel(episodes: List<PlayerEpisode>, seasonNumber: Int, episodeNumber: Int): String? {
    val season = episodes.filter { episode -> episode.seasonNumber == seasonNumber }.sortedBy { episode -> episode.episodeNumber }
    val index = season.indexOfFirst { episode -> episode.episodeNumber == episodeNumber }
    if (index < 0 || season.size < 2) {
        return null
    }
    return "Episode ${index + 1} of ${season.size}"
}
