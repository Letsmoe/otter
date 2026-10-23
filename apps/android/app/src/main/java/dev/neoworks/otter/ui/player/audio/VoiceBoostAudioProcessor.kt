package dev.neoworks.otter.ui.player.audio

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import kotlin.math.roundToInt

private const val PCM_16_SCALE = 32768f

/**
 * Dialogue boost for 16-bit PCM; other encodings are not touched. Stays in the audio chain and is
 * switched live with [enabled]: while off, samples pass through unchanged.
 */
@UnstableApi
class VoiceBoostAudioProcessor : BaseAudioProcessor() {

    @Volatile
    var enabled = false

    private var dsp: VoiceBoostDsp? = null
    private var frame = FloatArray(0)
    private var wasEnabled = false

    override fun onConfigure(inputAudioFormat: AudioFormat): AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) {
            return AudioFormat.NOT_SET
        }
        dsp = VoiceBoostDsp(inputAudioFormat.sampleRate, inputAudioFormat.channelCount)
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
        val boosting = enabled
        if (boosting && !wasEnabled) {
            activeDsp?.reset()
        }
        wasEnabled = boosting
        if (!boosting || activeDsp == null) {
            output.put(inputBuffer)
        } else {
            boostFrames(activeDsp, inputBuffer, output)
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

    private fun boostFrames(activeDsp: VoiceBoostDsp, input: ByteBuffer, output: ByteBuffer) {
        val channelCount = inputAudioFormat.channelCount
        while (input.remaining() >= inputAudioFormat.bytesPerFrame) {
            for (channel in 0 until channelCount) {
                frame[channel] = input.short / PCM_16_SCALE
            }
            activeDsp.processFrame(frame)
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
