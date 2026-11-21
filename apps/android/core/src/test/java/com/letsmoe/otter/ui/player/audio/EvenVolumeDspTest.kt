package com.letsmoe.otter.ui.player.audio

import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.sqrt

private const val SAMPLE_RATE = 48_000

class EvenVolumeDspTest {

    @Test
    fun quietDialogueComesUp() {
        val dsp = EvenVolumeDsp(SAMPLE_RATE)

        val level = rmsAfter(dsp, amplitude = 0.02f, seconds = 8)

        // The tone's own RMS is 0.014; twice that is +6 dB.
        assertTrue("quiet tone ends at RMS $level", level > 0.02f / sqrt(2f) * 2f)
    }

    @Test
    fun loudScenesGoDown() {
        val dsp = EvenVolumeDsp(SAMPLE_RATE)

        val level = rmsAfter(dsp, amplitude = 0.8f, seconds = 8)

        assertTrue("loud tone ends at RMS $level", level < 0.8f / sqrt(2f) * 0.5f)
    }

    @Test
    fun aSuddenBangIsCaughtBeforeItClips() {
        val dsp = EvenVolumeDsp(SAMPLE_RATE)
        rmsAfter(dsp, amplitude = 0.02f, seconds = 8)

        var peak = 0f
        val frame = FloatArray(2)
        repeat(SAMPLE_RATE / 10) { index ->
            frame.fill(toneSample(1f, index))
            dsp.process(frame)
            peak = maxOf(peak, abs(frame[0]))
        }

        assertTrue("peak $peak", peak <= 0.96f)
    }

    @Test
    fun pausesDoNotPumpUpTheNoise() {
        val dsp = EvenVolumeDsp(SAMPLE_RATE)
        rmsAfter(dsp, amplitude = 0.1f, seconds = 4)

        val hiss = rmsAfter(dsp, amplitude = 0.001f, seconds = 8)

        assertTrue("hiss ends at RMS $hiss", hiss < 0.001f * 1.5f)
    }
}

// Runs a stereo tone through [dsp] and measures the last second's RMS.
private fun rmsAfter(dsp: EvenVolumeDsp, amplitude: Float, seconds: Int): Float {
    val frame = FloatArray(2)
    var sum = 0.0
    val total = SAMPLE_RATE * seconds
    for (index in 0 until total) {
        frame.fill(toneSample(amplitude, index))
        dsp.process(frame)
        if (index >= total - SAMPLE_RATE) {
            sum += frame[0] * frame[0]
        }
    }
    return sqrt(sum / SAMPLE_RATE).toFloat()
}

private fun toneSample(amplitude: Float, index: Int): Float {
    return amplitude * sin(2 * PI * 440 * index / SAMPLE_RATE).toFloat()
}
