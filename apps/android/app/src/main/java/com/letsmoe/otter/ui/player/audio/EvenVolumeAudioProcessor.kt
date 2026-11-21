package com.letsmoe.otter.ui.player.audio

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import kotlin.math.roundToInt

private const val PCM_16_SCALE = 32768f

/**
 * Even volume for 16-bit PCM, like [VoiceBoostAudioProcessor]: always in the
 * chain, switched live with [enabled]; while off, samples pass through.
 */
@UnstableApi
open class EvenVolumeAudioProcessor : BaseAudioProcessor() {

    @Volatile
    var enabled = false

    private var dsp: EvenVolumeDsp? = null
    private var frame = FloatArray(0)
    private var wasEnabled = false

    override fun onConfigure(inputAudioFormat: AudioFormat): AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) {
            return AudioFormat.NOT_SET
        }
        dsp = EvenVolumeDsp(inputAudioFormat.sampleRate)
        frame = FloatArray(inputAudioFormat.channelCount)
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val size = inputBuffer.remaining()
        if (size == 0) {
            return
        }
        val output = replaceOutputBuffer(size)
        val activeDsp = dsp
        val leveling = enabled
        if (leveling && !wasEnabled) {
            activeDsp?.reset()
        }
        wasEnabled = leveling
        if (!leveling || activeDsp == null) {
            output.put(inputBuffer)
        } else {
            levelFrames(activeDsp, inputBuffer, output)
        }
        output.flip()
    }

    override fun onFlush() {
        dsp?.reset()
    }

    override fun onReset() {
        dsp = null
        frame = FloatArray(0)
        wasEnabled = false
    }

    private fun levelFrames(activeDsp: EvenVolumeDsp, input: ByteBuffer, output: ByteBuffer) {
        val channelCount = inputAudioFormat.channelCount
        while (input.remaining() >= inputAudioFormat.bytesPerFrame) {
            for (channel in 0 until channelCount) {
                frame[channel] = input.short / PCM_16_SCALE
            }
            activeDsp.process(frame)
            for (channel in 0 until channelCount) {
                output.putShort(toPcm16(frame[channel]))
            }
        }
    }

    private fun toPcm16(sample: Float): Short {
        val scaled = (sample * PCM_16_SCALE).roundToInt()
        return scaled.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
    }
}
