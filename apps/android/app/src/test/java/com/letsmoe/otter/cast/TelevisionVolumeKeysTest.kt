package com.letsmoe.otter.cast

import org.junit.Assert.assertEquals
import org.junit.Test

class TelevisionVolumeKeysTest {

    @Test
    fun volumeUpRaisesTheTelevisionByOneStep() {
        assertEquals(33, volumeAfterPress(current = 30, direction = 1))
    }

    @Test
    fun volumeDownLowersItByOneStep() {
        assertEquals(27, volumeAfterPress(current = 30, direction = -1))
    }

    @Test
    fun theVolumeStaysBetweenSilenceAndFull() {
        assertEquals(0, volumeAfterPress(current = 1, direction = -1))
        assertEquals(100, volumeAfterPress(current = 99, direction = 1))
    }
}
