package com.letsmoe.otter.ui.player

import kotlinx.coroutines.CoroutineScope

/** A link found and resolved ahead of time, with the stored link it came from when that is known. */
data class PrefetchedLink(val playing: PlayingSource, val stream: SourceStream?)

/** Finds and resolves the links of one title in the background. */
interface LinkSearch {
    fun start(mediaId: String)

    /** The resolved link; null while the search still runs or when it found none. */
    fun resolved(): PrefetchedLink?

    fun stop()
}

/** The same search the player runs for the title it plays: best language first, links the screen fits first. */
class StreamLinkSearch(scope: CoroutineScope, fitsScreen: (SourceStream) -> Boolean) : LinkSearch {

    private val playback = StreamPlayback(scope).also { search -> search.fitsScreen = fitsScreen }

    override fun start(mediaId: String) {
        playback.follow(mediaId)
        playback.play()
    }

    override fun resolved(): PrefetchedLink? {
        val state = playback.state.value
        val playing = state.playing ?: return null
        val stream = state.streams.firstOrNull { candidate -> candidate.id == playing.sourceId }
        return PrefetchedLink(playing, stream)
    }

    override fun stop() {
        playback.stop()
    }
}

/**
 * Looks for the next episode's link while the credits of this one run, so the next episode starts
 * at once instead of searching first. Only one episode is looked up at a time, and a link nobody
 * asked for is dropped.
 */
class NextEpisodePrefetch(private val newSearch: () -> LinkSearch) {

    private var search: LinkSearch? = null
    private var searchedMediaId: String? = null

    /** Starts looking for [nextMediaId]'s link; asking again for the same episode does nothing. */
    fun start(nextMediaId: String) {
        if (nextMediaId == searchedMediaId) {
            return
        }
        cancel()
        val started = newSearch()
        started.start(nextMediaId)
        search = started
        searchedMediaId = nextMediaId
    }

    /** The link found for [openedMediaId] when it is the episode looked up and the search is done; the search ends either way. */
    fun take(openedMediaId: String): PrefetchedLink? {
        var link: PrefetchedLink? = null
        if (openedMediaId == searchedMediaId) {
            link = search?.resolved()
        }
        cancel()
        return link
    }

    fun cancel() {
        search?.stop()
        search = null
        searchedMediaId = null
    }
}
