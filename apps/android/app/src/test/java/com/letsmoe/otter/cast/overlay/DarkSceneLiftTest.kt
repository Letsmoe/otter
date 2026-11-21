package com.letsmoe.otter.cast.overlay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DarkSceneLiftTest {

    @Test
    fun blackAndWhiteStayWhereTheyAre() {
        assertEquals(rgb(0, 0, 0), liftedColor(0.0, 0.0, 0.0))
        assertEquals(rgb(255, 255, 255), liftedColor(1.0, 1.0, 1.0))
    }

    @Test
    fun darkGreyOpensUpMoreThanLightGrey() {
        val darkGain = red(liftedColor(0.1, 0.1, 0.1)) - 26
        val lightGain = red(liftedColor(0.8, 0.8, 0.8)) - 204

        assertTrue("dark grey gains $darkGain, light grey $lightGain", darkGain > lightGain)
        assertTrue(darkGain > 10)
    }

    @Test
    fun aColourKeepsItsHue() {
        val lifted = liftedColor(0.2, 0.1, 0.05)

        val redToGreen = red(lifted).toDouble() / green(lifted)
        assertEquals(2.0, redToGreen, 0.1)
    }

    @Test
    fun theCubeIsIndexedRedGreenBlue() {
        val cube = liftCube()

        assertEquals(33, cube.size)
        assertTrue(red(cube[32][0][0]) > 200 && blue(cube[32][0][0]) == 0)
        assertTrue(blue(cube[0][0][32]) > 200 && red(cube[0][0][32]) == 0)
    }
}

private fun rgb(red: Int, green: Int, blue: Int): Int {
    return (0xFF shl 24) or (red shl 16) or (green shl 8) or blue
}

private fun red(color: Int): Int = (color shr 16) and 0xFF

private fun green(color: Int): Int = (color shr 8) and 0xFF

private fun blue(color: Int): Int = color and 0xFF
