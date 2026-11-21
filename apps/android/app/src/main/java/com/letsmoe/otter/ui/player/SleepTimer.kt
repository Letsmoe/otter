package com.letsmoe.otter.ui.player

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** The sound fades out over this long before the timer ends playback. */
const val SLEEP_FADE_MILLISECONDS = 20_000L

/** What the sleep timer sheet offers, besides "End of episode". */
val sleepTimerMinutes = listOf(15, 30, 60)

// A TV hears the sound this long after the phone encoded it, so its fade starts that much earlier.
const val TELEVISION_SOUND_LEAD_MILLISECONDS = 5_000L

/** A running timer: it was started for [minutes] and pauses playback at [endsAtMillis]. */
data class SleepTimerRun(val minutes: Int, val endsAtMillis: Long)

/** The volume (0 to 1) [remainingMillis] before the timer ends: full until the fade starts, then falling to silence. */
fun sleepGainAt(remainingMillis: Long): Float {
    if (remainingMillis >= SLEEP_FADE_MILLISECONDS) {
        return 1f
    }
    if (remainingMillis <= 0) {
        return 0f
    }
    val fraction = remainingMillis / SLEEP_FADE_MILLISECONDS.toFloat()
    // Squared, as the ear hears loudness: a straight fade seems to stay loud and drop at the end.
    return fraction * fraction
}

/**
 * The sleep timer: after the chosen minutes the sound fades out and playback pauses. One timer for
 * the whole app, so it carries over when playback moves between the phone and a TV.
 */
class SleepTimer(private val clockMillis: () -> Long = System::currentTimeMillis) {

    private val mutableRun = MutableStateFlow<SleepTimerRun?>(null)
    val run: StateFlow<SleepTimerRun?> = mutableRun

    fun start(minutes: Int) {
        mutableRun.value = SleepTimerRun(minutes, clockMillis() + minutes * 60_000L)
    }

    fun cancel() {
        mutableRun.value = null
    }

    /** Whether the time is up. */
    fun isDue(): Boolean {
        val current = mutableRun.value ?: return false
        return clockMillis() >= current.endsAtMillis
    }

    /** The volume now; [leadMillis] is how much later than now the sound is heard (a TV hears it late). */
    fun gain(leadMillis: Long = 0): Float {
        val current = mutableRun.value ?: return 1f
        return sleepGainAt(current.endsAtMillis - clockMillis() - leadMillis)
    }

    companion object {
        val shared = SleepTimer()
    }
}
