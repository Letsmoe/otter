package com.letsmoe.otter.ui.player.audio

import kotlin.math.exp
import kotlin.math.sqrt

// Where speech should sit: -20 dBFS RMS.
private const val TARGET_MEAN_SQUARE = 0.01f
// Gain stays between -12 dB and +12 dB.
private const val MIN_GAIN = 0.25f
private const val MAX_GAIN = 4f
// Below -50 dBFS is a pause, not quiet speech: the level is held instead of chased.
private const val SILENCE_MEAN_SQUARE = 0.00001f
private const val LEVEL_SECONDS = 3f
private const val GAIN_SECONDS = 0.5f
private const val LIMITER_RELEASE_SECONDS = 0.2f
private const val LIMITER_CEILING = 0.95f

/**
 * Evens out loud and quiet passages: a slow level follower turns quiet
 * dialogue up and loud scenes down, and a peak limiter catches what the slow
 * gain is too late for. Works on one frame (a sample per channel) at a time,
 * samples between -1 and 1.
 */
class EvenVolumeDsp(sampleRate: Int) {

    private val levelFollow = followRate(sampleRate, LEVEL_SECONDS)
    private val gainFollow = followRate(sampleRate, GAIN_SECONDS)
    private val limiterRelease = followRate(sampleRate, LIMITER_RELEASE_SECONDS)
    private var level = TARGET_MEAN_SQUARE
    private var gain = 1f
    private var limiterGain = 1f

    fun reset() {
        level = TARGET_MEAN_SQUARE
        gain = 1f
        limiterGain = 1f
    }

    /** Changes [frame] in place. */
    fun process(frame: FloatArray) {
        followLevel(meanSquareOf(frame))
        val wanted = sqrt(TARGET_MEAN_SQUARE / level).coerceIn(MIN_GAIN, MAX_GAIN)
        gain += (wanted - gain) * gainFollow
        limit(peakOf(frame) * gain)
        val total = gain * limiterGain
        for (channel in frame.indices) {
            frame[channel] *= total
        }
    }

    private fun followLevel(meanSquare: Float) {
        if (meanSquare < SILENCE_MEAN_SQUARE) {
            return
        }
        level += (meanSquare - level) * levelFollow
    }

    // Instant when a peak would clip, then back to unity.
    private fun limit(peak: Float) {
        if (peak * limiterGain > LIMITER_CEILING) {
            limiterGain = LIMITER_CEILING / peak
            return
        }
        limiterGain += (1f - limiterGain) * limiterRelease
    }
}

private fun followRate(sampleRate: Int, seconds: Float): Float {
    return 1f - exp(-1f / (sampleRate * seconds))
}

private fun meanSquareOf(frame: FloatArray): Float {
    var sum = 0f
    for (sample in frame) {
        sum += sample * sample
    }
    return sum / frame.size
}

private fun peakOf(frame: FloatArray): Float {
    var peak = 0f
    for (sample in frame) {
        peak = maxOf(peak, kotlin.math.abs(sample))
    }
    return peak
}
