package dev.neoworks.otter.ui.player.audio

import kotlin.math.abs
import kotlin.math.sign
import kotlin.math.tanh

private const val SURROUND_MIN_CHANNELS = 6
// Android channel order of 5.1 and up: front left, front right, front center, LFE, ...
private const val CENTER_CHANNEL_INDEX = 2
private const val CENTER_GAIN = 2.0f
private const val OTHER_CHANNEL_GAIN = 0.7f

private const val SPEECH_CENTER_HERTZ = 2500.0
private const val SPEECH_BAND_QUALITY = 0.7
// Adding the unity-gain band to the signal lifts the band by 6 dB.
private const val SPEECH_BAND_GAIN = 1.0f
private const val BASS_CORNER_HERTZ = 150.0
private const val BASS_CUT_DECIBELS = -4.0

// Below the threshold the limiter does nothing; above it the curve bends towards full scale.
private const val LIMITER_THRESHOLD = 0.8f

/**
 * Makes dialogue stand out. Surround mixes lift the center channel and lower the rest; stereo and
 * mono lift the speech band of the mid signal and cut the lows. A soft limiter keeps the result
 * within full scale. Works on one frame of floats in [-1, 1] at a time.
 */
class VoiceBoostDsp(sampleRate: Int, private val channelCount: Int) {

    private val isSurround = channelCount >= SURROUND_MIN_CHANNELS
    private val speechBand = Biquad.bandPass(sampleRate, SPEECH_CENTER_HERTZ, SPEECH_BAND_QUALITY)
    private val bassCuts = List(channelCount) { Biquad.lowShelf(sampleRate, BASS_CORNER_HERTZ, BASS_CUT_DECIBELS) }

    /** Rewrites one frame (one sample per channel) in place. */
    fun processFrame(frame: FloatArray) {
        if (isSurround) {
            mixSurround(frame)
        } else {
            liftSpeechBand(frame)
        }
        for (channel in 0 until channelCount) {
            frame[channel] = softLimit(frame[channel])
        }
    }

    fun reset() {
        speechBand.reset()
        bassCuts.forEach { bassCut -> bassCut.reset() }
    }

    private fun mixSurround(frame: FloatArray) {
        for (channel in 0 until channelCount) {
            frame[channel] *= surroundGain(channel)
        }
    }

    private fun surroundGain(channel: Int): Float {
        if (channel == CENTER_CHANNEL_INDEX) {
            return CENTER_GAIN
        }
        return OTHER_CHANNEL_GAIN
    }

    private fun liftSpeechBand(frame: FloatArray) {
        var mid = 0f
        for (channel in 0 until channelCount) {
            mid += frame[channel]
        }
        val speech = speechBand.process(mid / channelCount) * SPEECH_BAND_GAIN
        for (channel in 0 until channelCount) {
            frame[channel] = bassCuts[channel].process(frame[channel] + speech)
        }
    }
}

/** Identity up to [LIMITER_THRESHOLD], then a tanh knee that approaches but never exceeds full scale. */
fun softLimit(sample: Float): Float {
    val magnitude = abs(sample)
    if (magnitude <= LIMITER_THRESHOLD) {
        return sample
    }
    val headroom = 1f - LIMITER_THRESHOLD
    val limited = LIMITER_THRESHOLD + headroom * tanh((magnitude - LIMITER_THRESHOLD) / headroom)
    return sign(sample) * limited
}
