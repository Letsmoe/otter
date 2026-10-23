package dev.neoworks.otter.ui.player

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

const val UP_NEXT_MILLISECONDS = 10_000L

/** The next episode as the card during the credits shows it. */
data class UpNextCard(val episodeLabel: String, val stillUrl: String?, val startedAtMillis: Long)

/**
 * Offers the next episode once the credits start and plays it after the
 * countdown. The countdown runs here, not in an animation, so it also
 * finishes while the phone is locked and a TV plays.
 */
class UpNext(private val scope: CoroutineScope, private val playNext: () -> Unit) {

    private val mutableCard = MutableStateFlow<UpNextCard?>(null)
    val card: StateFlow<UpNextCard?> = mutableCard

    private var countdown: Job? = null
    // Cancelled during these credits: the viewer watches them.
    private var dismissed = false

    fun reset() {
        withdraw()
        dismissed = false
    }

    /** Called with every position update; going back before the credits withdraws the card. */
    fun follow(inCredits: Boolean, playing: Boolean, next: PlayerEpisode?) {
        if (!inCredits) {
            reset()
            return
        }
        if (dismissed || mutableCard.value != null || !playing || next == null) {
            return
        }
        offer(next)
    }

    fun cancel() {
        withdraw()
        dismissed = true
    }

    fun playNow() {
        withdraw()
        playNext()
    }

    private fun offer(next: PlayerEpisode) {
        mutableCard.value = UpNextCard(episodeLabel(next), next.stillUrl, System.currentTimeMillis())
        countdown = scope.launch {
            delay(UP_NEXT_MILLISECONDS)
            mutableCard.value = null
            playNext()
        }
    }

    private fun withdraw() {
        countdown?.cancel()
        countdown = null
        mutableCard.value = null
    }
}

private fun episodeLabel(episode: PlayerEpisode): String {
    return "${episodeCode(episode.seasonNumber, episode.episodeNumber)} · ${episodeName(episode.name, episode.episodeNumber)}"
}
