package com.letsmoe.otter.ui.player

// Without timed credits, the last 3% of the title counts as its credits.
private const val UNTIMED_CREDITS_FRACTION = 0.03

/**
 * Tells once per title when it counts as watched: as its credits start, or without timed credits
 * as the last stretch of the file starts.
 */
class FinishWatch {

    private var finished = false

    fun reset() {
        finished = false
    }

    /** True the first time the credits are reached; [inCredits] is false for titles without timed credits. */
    fun reached(inCredits: Boolean, positionSeconds: Double, durationSeconds: Double): Boolean {
        if (finished || durationSeconds <= 0) {
            return false
        }
        if (!inCredits && positionSeconds < durationSeconds * (1 - UNTIMED_CREDITS_FRACTION)) {
            return false
        }
        finished = true
        return true
    }
}
