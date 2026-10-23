package dev.neoworks.otter.ui.player.audio

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** One second-order IIR filter section (transposed direct form II); coefficients follow the RBJ audio EQ cookbook. */
class Biquad private constructor(
    private val feedForward0: Double,
    private val feedForward1: Double,
    private val feedForward2: Double,
    private val feedback1: Double,
    private val feedback2: Double,
) {

    private var state1 = 0.0
    private var state2 = 0.0

    fun process(input: Float): Float {
        val output = feedForward0 * input + state1
        state1 = feedForward1 * input - feedback1 * output + state2
        state2 = feedForward2 * input - feedback2 * output
        return output.toFloat()
    }

    fun reset() {
        state1 = 0.0
        state2 = 0.0
    }

    companion object {

        /** Passes a band around [centerHertz] at unity gain at the center and silences the rest. */
        fun bandPass(sampleRate: Int, centerHertz: Double, quality: Double): Biquad {
            val omega = 2.0 * PI * centerHertz / sampleRate
            val alpha = sin(omega) / (2.0 * quality)
            val normalizer = 1.0 + alpha
            return Biquad(alpha / normalizer, 0.0, -alpha / normalizer, -2.0 * cos(omega) / normalizer, (1.0 - alpha) / normalizer)
        }

        /** Raises or lowers everything below [cornerHertz] by [gainDecibels]. */
        fun lowShelf(sampleRate: Int, cornerHertz: Double, gainDecibels: Double): Biquad {
            val amplitude = 10.0.pow(gainDecibels / 40.0)
            val omega = 2.0 * PI * cornerHertz / sampleRate
            val cosine = cos(omega)
            val alpha = sin(omega) / 2.0 * sqrt(2.0)
            val rootTerm = 2.0 * sqrt(amplitude) * alpha
            val normalizer = (amplitude + 1) + (amplitude - 1) * cosine + rootTerm
            return Biquad(
                amplitude * ((amplitude + 1) - (amplitude - 1) * cosine + rootTerm) / normalizer,
                2 * amplitude * ((amplitude - 1) - (amplitude + 1) * cosine) / normalizer,
                amplitude * ((amplitude + 1) - (amplitude - 1) * cosine - rootTerm) / normalizer,
                -2 * ((amplitude - 1) + (amplitude + 1) * cosine) / normalizer,
                ((amplitude + 1) + (amplitude - 1) * cosine - rootTerm) / normalizer,
            )
        }
    }
}
