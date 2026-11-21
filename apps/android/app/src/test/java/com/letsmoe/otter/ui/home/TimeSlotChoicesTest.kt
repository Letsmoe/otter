package com.letsmoe.otter.ui.home

import com.letsmoe.otter.settings.Bedtime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TimeSlotChoicesTest {

    private fun minutes(hour: Int, minute: Int = 0): Int = hour * 60 + minute

    @Test
    fun withoutABedtimeOnlyTheFixedSlotsAreOffered() {
        val choices = timeSlotChoices(bedtimeMinutes = null, nowMinutes = minutes(20))

        assertEquals(listOf(20, 45, 90), choices.map { choice -> choice.minutes })
        assertFalse(choices.any { choice -> choice.untilBedtime })
    }

    @Test
    fun aBedtimeAheadAddsAnUntilBedtimeSlotWithTheTimeLeft() {
        val choices = timeSlotChoices(bedtimeMinutes = minutes(23), nowMinutes = minutes(21, 30))

        val untilBedtime = choices.last()
        assertTrue(untilBedtime.untilBedtime)
        assertEquals(90, untilBedtime.minutes)
    }

    @Test
    fun theTimeLeftCrossesMidnight() {
        val choices = timeSlotChoices(bedtimeMinutes = minutes(1), nowMinutes = minutes(23, 15))

        assertEquals(105, choices.last().minutes)
    }

    @Test
    fun noUntilBedtimeSlotOnceTheNightHasStarted() {
        val choices = timeSlotChoices(bedtimeMinutes = minutes(23), nowMinutes = minutes(23, 30))

        assertFalse(choices.any { choice -> choice.untilBedtime })
    }

    @Test
    fun minutesUntilBedtimeIsNullDuringTheNight() {
        assertNull(Bedtime.minutesUntil(minutes(23), minutes(2)))
        assertEquals(60, Bedtime.minutesUntil(minutes(23), minutes(22)))
    }
}
