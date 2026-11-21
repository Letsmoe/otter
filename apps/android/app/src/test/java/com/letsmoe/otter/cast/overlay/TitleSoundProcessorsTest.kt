package com.letsmoe.otter.cast.overlay

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.audio.SonicAudioProcessor
import com.letsmoe.otter.ui.player.audio.AudioDelayAudioProcessor
import com.letsmoe.otter.ui.player.audio.SleepFadeAudioProcessor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class TitleSoundProcessorsTest {

    private fun job(delayMillis: Int, gain: Float): OverlayJob {
        val live = OverlayLiveInputs(audioDelayMillis = { delayMillis }, audioGain = { gain })
        return OverlayJob("http://127.0.0.1/title.m3u8", null, 0.0, 1080, OverlayPlan(), live, onFailure = { _, _ -> })
    }

    @Test
    fun theDelayAndTheFadeWorkOnTheConvertedSound() {
        val conversion = SonicAudioProcessor()

        val processors = titleSoundProcessors(job(0, 1f), listOf(conversion))

        val delayIndex = processors.indexOfFirst { processor -> processor is AudioDelayAudioProcessor }
        val fadeIndex = processors.indexOfFirst { processor -> processor is SleepFadeAudioProcessor }
        assertTrue(processors.indexOf(conversion) < delayIndex)
        assertEquals(processors.size - 1, fadeIndex)
    }

    @Test
    fun aLaterSoundGetsSilenceCountedInFramesOfTheSessionsRate() {
        val delay = titleSoundProcessors(job(50, 1f)).first { processor -> processor is AudioDelayAudioProcessor }
        delay.configure(AudioFormat(48_000, 2, C.ENCODING_PCM_16BIT))
        delay.flush()

        delay.queueInput(pcmFrames(100))

        // 50 ms at 48 kHz are 2400 frames of 4 bytes, in front of the 100 frames put in.
        assertEquals((2400 + 100) * 4, delay.output.remaining())
    }

    private fun pcmFrames(frames: Int): ByteBuffer {
        val buffer = ByteBuffer.allocateDirect(frames * 4).order(ByteOrder.nativeOrder())
        repeat(frames * 2) { buffer.putShort(1000) }
        buffer.flip()
        return buffer
    }
}
