package com.letsmoe.otter.cast.overlay

import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.SingleColorLut
import kotlin.math.pow

// A 33-point cube is smooth enough for a gentle curve and small to upload.
private const val CUBE_SIZE = 33
// Below 1 lifts dark tones the most and leaves white where it is.
private const val LIFT_GAMMA = 0.75

// Rec. 709 weights for the brightness of a colour.
private const val RED_WEIGHT = 0.2126
private const val GREEN_WEIGHT = 0.7152
private const val BLUE_WEIGHT = 0.0722

/**
 * Lifts the shadows of the TV's picture before anything is drawn into it:
 * each colour's brightness goes through a gamma curve and the colour is
 * scaled with it, so dark scenes open up without washing out their hues.
 */
@UnstableApi
fun darkSceneLift(): SingleColorLut {
    return SingleColorLut.createFromCube(liftCube())
}

/** The lookup cube, indexed [red][green][blue] as Media3 reads it. */
fun liftCube(): Array<Array<IntArray>> {
    return Array(CUBE_SIZE) { red ->
        Array(CUBE_SIZE) { green ->
            IntArray(CUBE_SIZE) { blue -> liftedColor(red.toCubeLevel(), green.toCubeLevel(), blue.toCubeLevel()) }
        }
    }
}

/** One colour, channels from 0 to 1, lifted as the cube does; ARGB. */
fun liftedColor(red: Double, green: Double, blue: Double): Int {
    val brightness = RED_WEIGHT * red + GREEN_WEIGHT * green + BLUE_WEIGHT * blue
    if (brightness <= 0.0) {
        return opaque(0, 0, 0)
    }
    val factor = brightness.pow(LIFT_GAMMA) / brightness
    return opaque(toByte(red * factor), toByte(green * factor), toByte(blue * factor))
}

private fun opaque(red: Int, green: Int, blue: Int): Int {
    return (0xFF shl 24) or (red shl 16) or (green shl 8) or blue
}

private fun Int.toCubeLevel(): Double {
    return this.toDouble() / (CUBE_SIZE - 1)
}

private fun toByte(level: Double): Int {
    return (level.coerceIn(0.0, 1.0) * 255 + 0.5).toInt()
}
