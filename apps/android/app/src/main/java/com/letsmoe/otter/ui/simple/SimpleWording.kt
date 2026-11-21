package com.letsmoe.otter.ui.simple

import com.letsmoe.otter.graphql.type.MediaType
import com.letsmoe.otter.ui.media.RailItem

private val episodeCodePattern = Regex("""^S(\d+)E(\d+)(?: · (.*))?$""")

/** "S1E3 · Name" in plain words: "Season 1, episode 3 · Name"; anything else stays as it is. */
fun plainEpisodeLabel(subtitle: String): String {
    val match = episodeCodePattern.matchEntire(subtitle)
    if (match == null) {
        return subtitle
    }
    val season = match.groupValues[1]
    val episode = match.groupValues[2]
    val name = match.groupValues[3]
    val code = "Season $season, episode $episode"
    if (name.isEmpty()) {
        return code
    }
    return "$code · $name"
}

/** The line under a title in simple mode: the episode to watch, else "Movie" or "TV show" with the year. */
fun simpleSubtitle(item: RailItem): String {
    val episodeLine = item.subtitle
    if (item.episodeId != null && episodeLine != null) {
        return plainEpisodeLabel(episodeLine)
    }
    val kind = kindWord(item.mediaType)
    if (item.year == null) {
        return kind
    }
    return "$kind · ${item.year}"
}

private fun kindWord(mediaType: MediaType): String {
    if (mediaType == MediaType.SERIES) {
        return "TV show"
    }
    return "Movie"
}

/**
 * The movie or episode to play when [item] is tapped, or null when a series still needs its
 * next episode looked up.
 */
fun directPlayId(item: RailItem): String? {
    if (item.mediaType != MediaType.SERIES) {
        return item.id
    }
    return item.episodeId
}

/** What it takes to leave simple mode: the profile's PIN when it has one, else a confirmation. */
enum class LeaveCheck { PIN, CONFIRMATION }

fun leaveCheckFor(profileHasPin: Boolean): LeaveCheck {
    if (profileHasPin) {
        return LeaveCheck.PIN
    }
    return LeaveCheck.CONFIRMATION
}
