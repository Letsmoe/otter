package com.letsmoe.otter.ui.player.audio

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import kotlin.math.roundToInt

/** [sample] scaled by [gain] (0 to 1), kept within 16 bits. */
fun scalePcm16(sample: Short, gain: Float): Short {
    val scaled = (sample * gain).roundToInt()
    return scaled.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
}

/**
 * Fades the sound of 16-bit PCM with the gain [currentGain] answers, read for every buffer, so the
 * sleep timer can fade the TV's sound while the phone encodes it. Always in the chain; at full
 * gain the samples pass through untouched.
 */
@UnstableApi
open class SleepFadeAudioProcessor : BaseAudioProcessor() {

    /** The gain (0 to 1) of the buffer queued next. */
    protected open fun currentGain(): Float = 1f

    override fun onConfigure(inputAudioFormat: AudioFormat): AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) {
            return AudioFormat.NOT_SET
        }
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val size = inputBuffer.remaining()
        if (size == 0) {
            return
        }
        val output = replaceOutputBuffer(size)
        val gain = currentGain()
        if (gain >= 1f) {
            output.put(inputBuffer)
        } else {
            fadeSamples(inputBuffer, output, gain)
        }
        output.flip()
    }

    private fun fadeSamples(input: ByteBuffer, output: ByteBuffer, gain: Float) {
        while (input.remaining() >= 2) {
            output.putShort(scalePcm16(input.short, gain))
        }
    }
}
