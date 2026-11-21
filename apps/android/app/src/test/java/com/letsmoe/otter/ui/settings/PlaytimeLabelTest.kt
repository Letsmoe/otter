package com.letsmoe.otter.ui.settings

import com.letsmoe.otter.downloads.SmartQuality
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaytimeLabelTest {

    @Test
    fun theBudgetIsNamedInHoursOfVideo() {
        assertEquals("about 10 hours of video", playtimeLabel(10, SmartQuality.HD720))
        assertEquals("about 4 hours of video", playtimeLabel(10, SmartQuality.HD1080))
    }

    @Test
    fun halfHoursAreKept() {
        assertEquals("about 1.5 hours of video", playtimeLabel(4, SmartQuality.HD1080))
    }

    @Test
    fun aSingleHourIsSingular() {
        assertEquals("about 1 hour of video", playtimeLabel(1, SmartQuality.HD720))
    }

    @Test
    fun lessThanAnHourIsInMinutes() {
        assertEquals("about 48 minutes of video", playtimeLabel(2, SmartQuality.HD1080))
    }
}
