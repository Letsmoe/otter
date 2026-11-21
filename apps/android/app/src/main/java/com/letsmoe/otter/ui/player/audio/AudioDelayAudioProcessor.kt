package com.letsmoe.otter.ui.player.audio

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer

/**
 * Moves the sound against the picture for 16-bit PCM, by the delay [currentDelayMillis] answers
 * (read for every buffer, so it can change while a stream plays): silence is put in for a later
 * sound, frames are dropped for an earlier one (see [AudioShift]). Always in the chain; at 0 ms the
 * samples pass through untouched.
 */
@UnstableApi
open class AudioDelayAudioProcessor : BaseAudioProcessor() {

    private val shift = AudioShift()

    /** The wanted delay in milliseconds; positive plays the sound later than the picture. */
    protected open fun currentDelayMillis(): Int = 0

    override fun onConfigure(inputAudioFormat: AudioFormat): AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) {
            return AudioFormat.NOT_SET
        }
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val bytesPerFrame = inputAudioFormat.bytesPerFrame
        val inputFrames = inputBuffer.remaining() / bytesPerFrame
        if (inputFrames == 0) {
            return
        }
        shift.moveTo(currentDelayMillis() * inputAudioFormat.sampleRate / 1000L)
        val silenceFrames = shift.takeSilence()
        val droppedFrames = shift.takeDrop(inputFrames)
        val outputFrames = silenceFrames + inputFrames - droppedFrames
        if (outputFrames == 0) {
            inputBuffer.position(inputBuffer.limit())
            return
        }
        val output = replaceOutputBuffer(outputFrames * bytesPerFrame)
        output.put(ByteArray(silenceFrames * bytesPerFrame))
        inputBuffer.position(inputBuffer.position() + droppedFrames * bytesPerFrame)
        output.put(inputBuffer)
        output.flip()
    }

    override fun onFlush() {
        shift.reset()
    }

    override fun onReset() {
        shift.reset()
    }
}
