package com.letsmoe.otter.ui.player

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

// Episodes in a row without a touch on the phone before the next one asks first.
const val STILL_WATCHING_EPISODES = 3
const val STILL_WATCHING_ANSWER_MILLISECONDS = 30_000L

/**
 * Asks "Still watching?" before an episode starts on its own once [STILL_WATCHING_EPISODES]
 * episodes in a row ran without a touch on the phone, so a sleeping viewer isn't played on all
 * night. Without an answer within [STILL_WATCHING_ANSWER_MILLISECONDS] [onTimeout] stops playback.
 */
class StillWatching(
    private val scope: CoroutineScope,
    private val answerMilliseconds: Long = STILL_WATCHING_ANSWER_MILLISECONDS,
    private val onTimeout: () -> Unit,
) {

    private val mutableAsking = MutableStateFlow(false)
    val asking: StateFlow<Boolean> = mutableAsking

    private var untouchedEpisodes = 0
    private var touchedThisEpisode = false
    private var timeout: Job? = null

    /** A new title starts. */
    fun episodeStarted() {
        touchedThisEpisode = false
    }

    /** Any touch on the phone; the question itself is only answered by its buttons. */
    fun touched() {
        if (mutableAsking.value) {
            return
        }
        touchedThisEpisode = true
    }

    /** The next episode is about to start by itself: true holds it back and asks. */
    fun holdsBackNext(): Boolean {
        if (mutableAsking.value) {
            return true
        }
        if (touchedThisEpisode) {
            untouchedEpisodes = 0
            return false
        }
        untouchedEpisodes++
        if (untouchedEpisodes < STILL_WATCHING_EPISODES) {
            return false
        }
        ask()
        return true
    }

    /** "Keep watching": the next episode may start, and the count begins again. */
    fun confirm() {
        closeQuestion()
        untouchedEpisodes = 0
        touchedThisEpisode = true
    }

    /** "Stop": closes the question without playing on. */
    fun decline() {
        closeQuestion()
        untouchedEpisodes = 0
    }

    private fun ask() {
        mutableAsking.value = true
        timeout = scope.launch {
            delay(answerMilliseconds)
            timeout = null
            mutableAsking.value = false
            untouchedEpisodes = 0
            onTimeout()
        }
    }

    private fun closeQuestion() {
        timeout?.cancel()
        timeout = null
        mutableAsking.value = false
    }
}
