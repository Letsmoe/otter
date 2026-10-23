package dev.neoworks.otter.ui.title

import androidx.compose.runtime.Composable

/** What opens the player: a movie or episode, optionally on one chosen link; without one the player picks the best. */
data class PlayRequest(val mediaId: String, val streamId: String? = null)

/** Places on the title pages where other features (Watch Together, downloads) plug in their buttons. */
class TitleSlots(
    /** Round 40dp glass button left of search in the floating nav. */
    val togetherButton: @Composable () -> Unit = {},
    /** The "Download" action in the action row of a movie; gets the movie id and its title. */
    val downloadButton: @Composable (mediaId: String, title: String) -> Unit = { _, _ -> },
    /** The download icon on an episode row; gets the episode id and a label like "S1:E2 Name". */
    val episodeDownloadButton: @Composable (mediaId: String, label: String) -> Unit = { _, _ -> },
)
