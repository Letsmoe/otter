package com.letsmoe.otter.ui.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QualityHeightTest {

    private fun streamWith(quality: String?): SourceStream {
        return SourceStream("id", "url", "plugin", null, quality, null, null, null)
    }

    @Test
    fun readsPixelQualities() {
        assertEquals(2160, qualityHeight(streamWith("2160p")))
        assertEquals(1080, qualityHeight(streamWith("1080p")))
        assertEquals(720, qualityHeight(streamWith(" 720P ")))
    }

    @Test
    fun readsNamedQualities() {
        assertEquals(2160, qualityHeight(streamWith("4K")))
        assertEquals(2160, qualityHeight(streamWith("UHD")))
    }

    @Test
    fun unknownQualitiesHaveNoHeight() {
        assertNull(qualityHeight(streamWith("HLS")))
        assertNull(qualityHeight(streamWith(null)))
        assertNull(qualityHeight(streamWith("")))
    }
}
