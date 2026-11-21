package com.letsmoe.otter.ui.player

const val NEXT_EPISODE_HEADING = "Next episode"
private const val NEXT_SEASON_HEADING = "Next season"
private const val SEASON_FINALE_HEADING = "Season finale"
private const val NOTHING_AFTER_LABEL = "Nothing after it yet"

/**
 * What the card shown during the credits says about what follows: a [heading] and a [label]. A
 * card that [countsDown] starts the next episode at the end; one that doesn't only informs.
 */
data class UpNextNote(val heading: String, val label: String, val countsDown: Boolean)

/**
 * The note for [next] after [current]. The first episode of another season is named with its season
 * ("Season 3 · Episode 1"); without a next episode the note says the season finale has nothing after
 * it yet. Null without a [current] episode and a [next] one, e.g. for movies.
 */
fun upNextNote(current: PlayerEpisode?, next: PlayerEpisode?): UpNextNote? {
    if (next != null) {
        return noteForNext(current, next)
    }
    if (current != null) {
        return UpNextNote(SEASON_FINALE_HEADING, NOTHING_AFTER_LABEL, countsDown = false)
    }
    return null
}

private fun noteForNext(current: PlayerEpisode?, next: PlayerEpisode): UpNextNote {
    if (current != null && current.seasonNumber != next.seasonNumber) {
        return UpNextNote(NEXT_SEASON_HEADING, seasonStartLabel(next), countsDown = true)
    }
    return UpNextNote(NEXT_EPISODE_HEADING, episodeLabel(next), countsDown = true)
}

/** "Season 3 · Episode 1 · Name"; the name is left out when the episode has none. */
fun seasonStartLabel(episode: PlayerEpisode): String {
    val position = "Season ${episode.seasonNumber} · Episode ${episode.episodeNumber}"
    if (episode.name.isNullOrEmpty()) {
        return position
    }
    return "$position · ${episode.name}"
}

/** The episode after [episodeId] in the series' order (specials left out), crossing seasons; null for the last one. */
fun List<PlayerEpisode>.episodeAfter(episodeId: String): PlayerEpisode? {
    val ordered = filter { episode -> episode.seasonNumber > 0 }
        .sortedWith(compareBy({ episode -> episode.seasonNumber }, { episode -> episode.episodeNumber }))
    val index = ordered.indexOfFirst { episode -> episode.id == episodeId }
    if (index < 0) {
        return null
    }
    return ordered.getOrNull(index + 1)
}
