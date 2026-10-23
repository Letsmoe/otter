package dev.neoworks.otter.ui.player.audio

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.sin
import kotlin.math.sqrt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private const val SAMPLE_RATE = 48000

class VoiceBoostTest {

    @Test
    fun softLimitLeavesQuietSamplesAlone() {
        assertEquals(0.5f, softLimit(0.5f), 0f)
        assertEquals(-0.8f, softLimit(-0.8f), 0f)
    }

    @Test
    fun softLimitNeverExceedsFullScale() {
        for (sample in listOf(0.9f, 1f, 2f, 10f, -3f, -100f)) {
            assertTrue(abs(softLimit(sample)) <= 1f)
        }
    }

    @Test
    fun fullScaleStereoNeverClips() {
        val dsp = VoiceBoostDsp(SAMPLE_RATE, 2)
        val frame = FloatArray(2)
        for (index in 0 until SAMPLE_RATE) {
            val value = fullScaleSquare(index)
            frame[0] = value
            frame[1] = value
            dsp.processFrame(frame)
            assertTrue(abs(frame[0]) <= 1f && abs(frame[1]) <= 1f)
        }
    }

    @Test
    fun fullScaleSurroundNeverClips() {
        val dsp = VoiceBoostDsp(SAMPLE_RATE, 6)
        val frame = FloatArray(6)
        for (index in 0 until SAMPLE_RATE) {
            frame.fill(fullScaleSquare(index))
            dsp.processFrame(frame)
            assertTrue(frame.all { sample -> abs(sample) <= 1f })
        }
    }

    @Test
    fun surroundLiftsTheCenterAndLowersTheRest() {
        val dsp = VoiceBoostDsp(SAMPLE_RATE, 6)
        val frame = FloatArray(6) { 0.1f }
        dsp.processFrame(frame)
        assertEquals(0.2f, frame[2], 1e-6f)
        for (channel in listOf(0, 1, 3, 4, 5)) {
            assertEquals(0.07f, frame[channel], 1e-6f)
        }
    }

    @Test
    fun stereoLiftsTheSpeechBand() {
        val gainDecibels = stereoGainDecibels(2500.0)
        assertTrue("speech band gain was $gainDecibels dB", gainDecibels in 5.0..7.0)
    }

    @Test
    fun stereoCutsTheBass() {
        val gainDecibels = stereoGainDecibels(50.0)
        assertTrue("bass gain was $gainDecibels dB", gainDecibels < -2.0)
    }

    @Test
    fun resetClearsFilterMemory() {
        val dsp = VoiceBoostDsp(SAMPLE_RATE, 2)
        val frame = FloatArray(2) { 0.5f }
        dsp.processFrame(frame)
        val firstOutput = frame[0]
        repeat(100) { dsp.processFrame(FloatArray(2) { 0.5f }) }
        dsp.reset()
        val again = FloatArray(2) { 0.5f }
        dsp.processFrame(again)
        assertEquals(firstOutput, again[0], 0f)
    }

    @Test
    fun disabledProcessorPassesSamplesThrough() {
        val processor = configuredProcessor(channelCount = 2)
        val input = pcm16(listOf<Short>(1000, -2000, 32767, -32768, 5, 6))
        val output = run(processor, input.duplicate())
        assertEquals(input.rewind(), output)
    }

    @Test
    fun enabledProcessorBoostsTheCenterOfSurround() {
        val processor = configuredProcessor(channelCount = 6)
        processor.enabled = true
        val output = run(processor, pcm16(List(6) { 3000.toShort() }))
        assertEquals(6000, output.getShort(2 * Short.SIZE_BYTES).toInt())
        assertEquals(2100, output.getShort(0).toInt())
    }

    @Test
    fun enabledProcessorKeepsFullScaleInRange() {
        val processor = configuredProcessor(channelCount = 2)
        processor.enabled = true
        val samples = List(2 * 480) { index -> if ((index / 2) % 20 < 10) Short.MAX_VALUE else Short.MIN_VALUE }
        val output = run(processor, pcm16(samples))
        while (output.hasRemaining()) {
            val sample = output.short.toInt()
            assertTrue(sample in Short.MIN_VALUE..Short.MAX_VALUE)
        }
    }

    @Test
    fun otherEncodingsAreNotProcessed() {
        val processor = VoiceBoostAudioProcessor()
        processor.configure(AudioFormat(SAMPLE_RATE, 2, C.ENCODING_PCM_FLOAT))
        processor.flush()
        assertFalse(processor.isActive)
    }

    private fun fullScaleSquare(index: Int): Float {
        if ((index / 24) % 2 == 0) {
            return 1f
        }
        return -1f
    }

    // Level change of an in-phase stereo sine at [frequencyHertz], measured after the filters settle.
    private fun stereoGainDecibels(frequencyHertz: Double): Double {
        val dsp = VoiceBoostDsp(SAMPLE_RATE, 2)
        val frame = FloatArray(2)
        var inputEnergy = 0.0
        var outputEnergy = 0.0
        for (index in 0 until SAMPLE_RATE) {
            val input = (0.05 * sin(2 * PI * frequencyHertz * index / SAMPLE_RATE)).toFloat()
            frame[0] = input
            frame[1] = input
            dsp.processFrame(frame)
            if (index >= SAMPLE_RATE / 2) {
                inputEnergy += input * input
                outputEnergy += frame[0] * frame[0]
            }
        }
        return 20 * log10(sqrt(outputEnergy / inputEnergy))
    }

    private fun configuredProcessor(channelCount: Int): VoiceBoostAudioProcessor {
        val processor = VoiceBoostAudioProcessor()
        processor.configure(AudioFormat(SAMPLE_RATE, channelCount, C.ENCODING_PCM_16BIT))
        processor.flush()
        return processor
    }

    private fun run(processor: VoiceBoostAudioProcessor, input: ByteBuffer): ByteBuffer {
        processor.queueInput(input)
        return processor.output
    }

    private fun pcm16(samples: List<Short>): ByteBuffer {
        val buffer = ByteBuffer.allocateDirect(samples.size * Short.SIZE_BYTES).order(ByteOrder.nativeOrder())
        samples.forEach { sample -> buffer.putShort(sample) }
        buffer.flip()
        return buffer
    }
}
