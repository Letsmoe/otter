package com.letsmoe.otter.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NightVolumeTest {

    @Test
    fun theLimitOnlyAppliesAfterBedtime() {
        assertEquals(30, NightVolume.maximum(limit = 30, pastBedtime = true))
        assertEquals(TELEVISION_VOLUME_RANGE, NightVolume.maximum(limit = 30, pastBedtime = false))
    }

    @Test
    fun withoutALimitTheTvMayBeAsLoudAsItGets() {
        assertEquals(TELEVISION_VOLUME_RANGE, NightVolume.maximum(limit = null, pastBedtime = true))
    }

    @Test
    fun requestsAboveTheMaximumAreCut() {
        assertEquals(30, NightVolume.capped(requested = 80, maximum = 30))
        assertEquals(20, NightVolume.capped(requested = 20, maximum = 30))
        assertEquals(0, NightVolume.capped(requested = -5, maximum = 30))
    }

    @Test
    fun aVolumeAboveTheMaximumIsTooLoud() {
        assertTrue(NightVolume.exceeds(volume = 31, maximum = 30))
        assertFalse(NightVolume.exceeds(volume = 30, maximum = 30))
    }
}
