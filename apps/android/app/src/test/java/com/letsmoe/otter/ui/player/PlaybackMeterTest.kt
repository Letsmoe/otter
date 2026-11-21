package com.letsmoe.otter.ui.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class PlaybackMeterTest {

    private var now = 0L
    private val meter = PlaybackMeter { now }

    private fun advance(milliseconds: Long) {
        now += milliseconds
    }

    private fun startAndRenderFirstFrame(afterMilliseconds: Long = 1_500) {
        meter.start("stream:7")
        advance(afterMilliseconds)
        meter.videoSize(1920, 804)
        meter.firstFrame()
        meter.playing(true)
    }

    @Test
    fun measuresTheTimeToTheFirstPicture() {
        startAndRenderFirstFrame(afterMilliseconds = 2_300)
        advance(60_000)

        val measurement = meter.finish(bitrateBps = 3_000_000)
        assertNotNull(measurement)
        assertEquals(2_300, measurement!!.firstFrameMillis)
        assertEquals(60_000, measurement.playedMillis)
        assertEquals(1920, measurement.widthPixels)
        assertEquals(804, measurement.heightPixels)
        assertEquals(3_000_000, measurement.bitrateBps)
        assertEquals("stream:7", measurement.streamId)
    }

    @Test
    fun countsBufferingAfterTheStartAsAStall() {
        startAndRenderFirstFrame()
        advance(30_000)
        meter.buffering(true, wantsToPlay = true)
        meter.playing(false)
        advance(4_000)
        meter.buffering(false, wantsToPlay = true)
        meter.playing(true)
        advance(30_000)

        val measurement = meter.finish(0)!!
        assertEquals(1, measurement.stalls)
        assertEquals(4_000, measurement.stallMillis)
        assertEquals(60_000, measurement.playedMillis)
    }

    @Test
    fun theFirstBufferingBeforeAPictureIsNoStall() {
        meter.start("stream:7")
        meter.buffering(true, wantsToPlay = true)
        advance(3_000)
        meter.buffering(false, wantsToPlay = true)
        meter.firstFrame()
        meter.playing(true)
        advance(30_000)

        assertEquals(0, meter.finish(0)!!.stalls)
    }

    @Test
    fun bufferingAfterASeekIsTheViewersDoing() {
        startAndRenderFirstFrame()
        advance(30_000)
        meter.seeked()
        advance(200)
        meter.buffering(true, wantsToPlay = true)
        advance(5_000)
        meter.buffering(false, wantsToPlay = true)
        advance(30_000)

        assertEquals(0, meter.finish(0)!!.stalls)
    }

    @Test
    fun bufferingWhilePausedIsNoStall() {
        startAndRenderFirstFrame()
        advance(30_000)
        meter.playing(false)
        meter.buffering(true, wantsToPlay = false)
        advance(5_000)
        meter.buffering(false, wantsToPlay = false)

        assertEquals(0, meter.finish(0)!!.stalls)
    }

    @Test
    fun aStallStillOpenWhenTheLinkIsLeftIsCounted() {
        startAndRenderFirstFrame()
        advance(20_000)
        meter.buffering(true, wantsToPlay = true)
        advance(7_000)

        val measurement = meter.finish(0)!!
        assertEquals(1, measurement.stalls)
        assertEquals(7_000, measurement.stallMillis)
    }

    @Test
    fun aLinkLeftBeforeItShowedAPictureSaysNothing() {
        meter.start("stream:7")
        advance(10_000)
        assertNull(meter.finish(0))
    }

    @Test
    fun aVeryShortPlayWithoutStallsSaysNothing() {
        startAndRenderFirstFrame()
        advance(4_000)
        assertNull(meter.finish(0))
    }

    @Test
    fun aShortPlayThatStalledIsStillReported() {
        startAndRenderFirstFrame()
        advance(3_000)
        meter.buffering(true, wantsToPlay = true)
        advance(8_000)
        meter.buffering(false, wantsToPlay = true)
        advance(2_000)
        assertNotNull(meter.finish(0))
    }

    @Test
    fun finishingEmptiesTheMeter() {
        startAndRenderFirstFrame()
        advance(40_000)
        meter.finish(0)
        assertNull(meter.finish(0))
    }
}
