package com.letsmoe.otter.ui.player

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

const val UP_NEXT_MILLISECONDS = 10_000L

/**
 * The next episode as the card during the credits shows it. A running
 * countdown ends at [endsAtMillis]; a paused one holds [pausedRemainingMillis].
 */
data class UpNextCard(
    val episodeLabel: String,
    val stillUrl: String?,
    val endsAtMillis: Long,
    val pausedRemainingMillis: Long? = null,
)

/** Milliseconds until the next episode plays, between 0 and the whole countdown. */
fun remainingMillisOf(card: UpNextCard): Long {
    val paused = card.pausedRemainingMillis
    if (paused != null) {
        return paused
    }
    return (card.endsAtMillis - System.currentTimeMillis()).coerceIn(0, UP_NEXT_MILLISECONDS)
}

/**
 * Offers the next episode once the credits start and plays it after the
 * countdown. The countdown runs here, not in an animation, so it also
 * finishes while the phone is locked and a TV plays. It holds while the
 * video is paused, on the phone or on the TV.
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
        val shown = mutableCard.value
        if (shown != null) {
            followPlayback(shown, playing)
            return
        }
        if (dismissed || !playing || next == null) {
            return
        }
        startCountdown(UpNextCard(episodeLabel(next), next.stillUrl, 0), UP_NEXT_MILLISECONDS)
    }

    fun cancel() {
        withdraw()
        dismissed = true
    }

    fun playNow() {
        withdraw()
        playNext()
    }

    private fun followPlayback(shown: UpNextCard, playing: Boolean) {
        val paused = shown.pausedRemainingMillis
        if (!playing && paused == null) {
            countdown?.cancel()
            mutableCard.value = shown.copy(pausedRemainingMillis = remainingMillisOf(shown))
            return
        }
        if (playing && paused != null) {
            startCountdown(shown, paused)
        }
    }

    private fun startCountdown(card: UpNextCard, remainingMillis: Long) {
        mutableCard.value = card.copy(endsAtMillis = System.currentTimeMillis() + remainingMillis, pausedRemainingMillis = null)
        countdown = scope.launch {
            delay(remainingMillis)
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

/** "S1:E2 · Name", as the next episode's card shows it. */
fun episodeLabel(episode: PlayerEpisode): String {
    val cardLabel = episode.cardLabel
    if (cardLabel != null) {
        return cardLabel
    }
    return "${episodeCode(episode.seasonNumber, episode.episodeNumber)} · ${episodeName(episode.name, episode.episodeNumber)}"
}
