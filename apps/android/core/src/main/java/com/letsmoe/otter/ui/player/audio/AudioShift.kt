package com.letsmoe.otter.ui.player.audio

/** How far the sound may be moved against the picture, either way. */
const val AUDIO_DELAY_LIMIT_MILLISECONDS = 500

/** The step of the audio delay buttons. */
const val AUDIO_DELAY_STEP_MILLISECONDS = 50

/** The delay after a step of [stepMillis] from [currentMillis]: whole steps, within the limit. */
fun steppedAudioDelay(currentMillis: Int, stepMillis: Int): Int {
    val stepped = currentMillis + stepMillis
    return stepped.coerceIn(-AUDIO_DELAY_LIMIT_MILLISECONDS, AUDIO_DELAY_LIMIT_MILLISECONDS)
}

/** "+150 ms", "−50 ms" or "0 ms", as the audio delay row shows it; positive plays the sound later. */
fun audioDelayLabel(delayMillis: Int): String {
    if (delayMillis == 0) {
        return "0 ms"
    }
    if (delayMillis > 0) {
        return "+$delayMillis ms"
    }
    return "−${-delayMillis} ms"
}

/**
 * Moves the sound against the picture by counting frames: a later sound gets silence put in front of
 * what follows, an earlier one loses the frames it is ahead by. Changing the target mid-stream
 * inserts or drops just the difference, so the sound stays where the viewer put it without a restart.
 */
class AudioShift {

    private var appliedFrames = 0L
    // Positive: silence still to put in. Negative: frames still to drop.
    private var owedFrames = 0L

    /** Moves the sound to [targetFrames] later than the picture (negative: earlier). */
    fun moveTo(targetFrames: Long) {
        owedFrames += targetFrames - appliedFrames
        appliedFrames = targetFrames
    }

    /** The frames of silence to put in front of the next buffer. */
    fun takeSilence(): Int {
        if (owedFrames <= 0) {
            return 0
        }
        val silence = owedFrames.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        owedFrames -= silence
        return silence
    }

    /** The frames to cut from the start of the next buffer of [bufferFrames] frames. */
    fun takeDrop(bufferFrames: Int): Int {
        if (owedFrames >= 0) {
            return 0
        }
        val drop = (-owedFrames).coerceAtMost(bufferFrames.toLong()).toInt()
        owedFrames += drop
        return drop
    }

    fun reset() {
        appliedFrames = 0
        owedFrames = 0
    }
}
