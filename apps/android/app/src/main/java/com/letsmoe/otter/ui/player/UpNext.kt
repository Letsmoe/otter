package com.letsmoe.otter.ui.player

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

const val UP_NEXT_MILLISECONDS = 10_000L
// How long the note about a season finale stays, as it has no countdown to end it.
const val SEASON_FINALE_MILLISECONDS = 15_000L

/**
 * The next episode as the card during the credits shows it. A running
 * countdown ends at [endsAtMillis]; a paused one holds [pausedRemainingMillis].
 * A card that doesn't [countsDown] only tells (see [UpNextNote]) and has no episode to play.
 */
data class UpNextCard(
    val episodeLabel: String,
    val stillUrl: String?,
    val endsAtMillis: Long,
    val pausedRemainingMillis: Long? = null,
    val heading: String = NEXT_EPISODE_HEADING,
    val countsDown: Boolean = true,
    /** How many filler episodes were jumped over to reach this one. */
    val skippedFiller: Int = 0,
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

    /**
     * Called with every position update; going back before the credits withdraws the card. [current]
     * is the episode playing: with it, the card names a next season's first episode, and without a
     * [next] one it says the season finale has nothing after it yet. Pass none to offer nothing then.
     * [skippedFiller] is how many filler episodes were jumped over to reach [next].
     */
    fun follow(inCredits: Boolean, playing: Boolean, next: PlayerEpisode?, current: PlayerEpisode? = null, skippedFiller: Int = 0) {
        if (!inCredits) {
            reset()
            return
        }
        val shown = mutableCard.value
        if (shown != null) {
            followPlayback(shown, playing)
            return
        }
        if (dismissed || !playing) {
            return
        }
        val note = upNextNote(current, next) ?: return
        if (note.countsDown) {
            val card = UpNextCard(note.label, next?.stillUrl, 0, heading = note.heading, skippedFiller = skippedFiller)
            startCountdown(card, UP_NEXT_MILLISECONDS)
            return
        }
        showFinale(note)
    }

    fun cancel() {
        withdraw()
        dismissed = true
    }

    fun playNow() {
        if (mutableCard.value?.countsDown == false) {
            return
        }
        withdraw()
        playNext()
    }

    // Shown for a while, then gone for these credits.
    private fun showFinale(note: UpNextNote) {
        mutableCard.value = UpNextCard(note.label, null, 0, heading = note.heading, countsDown = false)
        countdown = scope.launch {
            delay(SEASON_FINALE_MILLISECONDS)
            mutableCard.value = null
            dismissed = true
        }
    }

    private fun followPlayback(shown: UpNextCard, playing: Boolean) {
        if (!shown.countsDown) {
            return
        }
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

/** "Skipping 3 filler episodes", under the card's episode when filler was jumped over. */
fun skippedFillerText(skippedFiller: Int): String? {
    if (skippedFiller <= 0) {
        return null
    }
    if (skippedFiller == 1) {
        return "Skipping 1 filler episode"
    }
    return "Skipping $skippedFiller filler episodes"
}

/** The label of the card drawn into a TV's picture: the episode, and "· skips 3 filler" when filler was jumped over. */
fun upNextLabel(episode: PlayerEpisode, skippedFiller: Int): String {
    return labelWithSkippedFiller(episodeLabel(episode), skippedFiller)
}

/** [label] with "· skips 3 filler" when filler episodes were jumped over. */
fun labelWithSkippedFiller(label: String, skippedFiller: Int): String {
    if (skippedFiller <= 0) {
        return label
    }
    return "$label · skips $skippedFiller filler"
}

/** "S1:E2 · Name", as the next episode's card shows it. */
fun episodeLabel(episode: PlayerEpisode): String {
    val cardLabel = episode.cardLabel
    if (cardLabel != null) {
        return cardLabel
    }
    return "${episodeCode(episode.seasonNumber, episode.episodeNumber)} · ${episodeName(episode.name, episode.episodeNumber)}"
}
