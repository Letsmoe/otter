package com.letsmoe.otter.ui.title

/**
 * The episode a series page opens on: by id (Continue Watching, notifications) or by its
 * numbers (shared links, which can't know this install's episode ids).
 */
data class LinkedEpisode(val seasonNumber: Int? = null, val episodeId: String? = null, val episodeNumber: Int? = null)

/** What opens the player: a movie or episode, optionally on one chosen link; without one the player picks the best. */
data class PlayRequest(val mediaId: String, val streamId: String? = null)
