package com.letsmoe.otter.settings

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BedtimeTest {

    private fun minutes(hour: Int, minute: Int = 0): Int = hour * 60 + minute

    @Test
    fun theNightStartsAtTheBedtime() {
        assertFalse(Bedtime.hasStarted(minutes(23), minutes(22, 59)))
        assertTrue(Bedtime.hasStarted(minutes(23), minutes(23)))
        assertTrue(Bedtime.hasStarted(minutes(23), minutes(23, 30)))
    }

    @Test
    fun theNightContinuesPastMidnight() {
        assertTrue(Bedtime.hasStarted(minutes(23), minutes(0)))
        assertTrue(Bedtime.hasStarted(minutes(23), minutes(6, 59)))
    }

    @Test
    fun theNightEndsInTheMorning() {
        assertFalse(Bedtime.hasStarted(minutes(23), minutes(7)))
        assertFalse(Bedtime.hasStarted(minutes(23), minutes(12)))
    }

    @Test
    fun aBedtimeAfterMidnightStartsTheNightLater() {
        assertFalse(Bedtime.hasStarted(minutes(1), minutes(0, 59)))
        assertTrue(Bedtime.hasStarted(minutes(1), minutes(1)))
        assertTrue(Bedtime.hasStarted(minutes(1), minutes(8, 59)))
        assertFalse(Bedtime.hasStarted(minutes(1), minutes(9)))
    }
}
