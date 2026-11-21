package com.letsmoe.otter.ui.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BetterLinkTest {

    private val minute = 60_000L

    private fun link(id: String, height: Int?, language: String? = "de", playable: Boolean? = true) =
        LinkInfo(id, height, language, playable, "${height}p")

    private val playing = link("playing", 720)
    private val sharper = link("sharper", 1080)

    @Test
    fun theSharpestPlayableLinkInTheSameLanguageIsBetter() {
        val links = listOf(playing, link("a", 1080), link("b", 2160, language = "en"), link("c", 1440, playable = null), link("d", 1440))

        assertEquals("d", pickBetterLink(playing, links, emptySet())?.id)
    }

    @Test
    fun linksThatAreNotSharperOrWereTriedAreNotBetter() {
        val links = listOf(playing, link("same", 720), link("lower", 480), sharper)

        assertNull(pickBetterLink(playing, links - sharper, emptySet()))
        assertNull(pickBetterLink(playing, links, tried = setOf("sharper")))
    }

    @Test
    fun withoutKnowingThePlayingHeightNothingIsBetter() {
        assertNull(pickBetterLink(link("playing", null), listOf(sharper), emptySet()))
    }

    @Test
    fun theBandwidthMustBeTwiceWhatTheLinkNeeds() {
        assertTrue(bandwidthSuffices(10_000_000, 1080))
        assertEquals(false, bandwidthSuffices(9_000_000, 1080))
        assertTrue(bandwidthSuffices(5_000_000, 720))
    }

    private class Switches {
        var switched = mutableListOf<String>()
        var castingNotes = 0
    }

    private var now = 0L
    private val switches = Switches()
    private val watcher = BetterLinkWatcher(
        clock = { now },
        onSwitch = { link -> switches.switched.add(link.id) },
        onCastingSkipped = { switches.castingNotes++ },
    )

    private fun snapshot(
        paused: Boolean = false,
        casting: Boolean = false,
        viewerChose: Boolean = false,
        bandwidth: Long = 20_000_000,
        caption: Boolean = false,
    ) = PlaybackSnapshot(paused, casting, viewerChose, playing, listOf(playing, sharper), bandwidth, caption)

    // Looks twice a few seconds apart: the first look notes the quiet moment, the second finds it long enough.
    private fun lookAtQuietMoment(snapshot: PlaybackSnapshot) {
        watcher.check { snapshot }
        now += 2_000
        watcher.check { snapshot }
    }

    @Test
    fun aStableQuietPlaybackWithPlentyOfBandwidthSwitches() {
        watcher.onPlaybackStarted()
        now += 4 * minute

        lookAtQuietMoment(snapshot())

        assertEquals(listOf("sharper"), switches.switched)
    }

    @Test
    fun playbackThatJustStartedIsNotStableYet() {
        watcher.onPlaybackStarted()
        now += 1 * minute

        lookAtQuietMoment(snapshot())

        assertTrue(switches.switched.isEmpty())
    }

    @Test
    fun aRebufferRestartsTheWaitForStability() {
        watcher.onPlaybackStarted()
        now += 4 * minute
        watcher.onRebuffer()
        now += 1 * minute

        lookAtQuietMoment(snapshot())

        assertTrue(switches.switched.isEmpty())
    }

    @Test
    fun withoutPlentyOfBandwidthItStays() {
        watcher.onPlaybackStarted()
        now += 4 * minute

        lookAtQuietMoment(snapshot(bandwidth = 6_000_000))

        assertTrue(switches.switched.isEmpty())
    }

    @Test
    fun itWaitsForAGapBetweenCaptions() {
        watcher.onPlaybackStarted()
        now += 4 * minute

        lookAtQuietMoment(snapshot(caption = true))
        assertTrue(switches.switched.isEmpty())

        now += 2_000
        lookAtQuietMoment(snapshot(caption = false))
        assertEquals(listOf("sharper"), switches.switched)
    }

    @Test
    fun aPausedPlaybackIsQuietAtOnce() {
        watcher.onPlaybackStarted()
        now += 4 * minute

        watcher.check { snapshot(paused = true, caption = true) }

        assertEquals(listOf("sharper"), switches.switched)
    }

    @Test
    fun aLinkTheViewerPickedIsLeftAlone() {
        watcher.onPlaybackStarted()
        now += 4 * minute

        lookAtQuietMoment(snapshot(viewerChose = true))

        assertTrue(switches.switched.isEmpty())
    }

    @Test
    fun aSwitchedToLinkIsNotTriedAgain() {
        watcher.onPlaybackStarted()
        now += 4 * minute
        lookAtQuietMoment(snapshot())

        now += 10 * minute
        lookAtQuietMoment(snapshot())

        assertEquals(1, switches.switched.size)
    }

    @Test
    fun whileCastingItNeverSwitchesAndSaysSoOnce() {
        watcher.onPlaybackStarted()
        now += 4 * minute

        lookAtQuietMoment(snapshot(casting = true))
        lookAtQuietMoment(snapshot(casting = true))

        assertTrue(switches.switched.isEmpty())
        assertEquals(1, switches.castingNotes)
    }

    @Test
    fun aNewTitleStartsOverWithoutWhatWasLearned() {
        watcher.onPlaybackStarted()
        now += 4 * minute
        lookAtQuietMoment(snapshot(casting = true))

        watcher.reset()
        lookAtQuietMoment(snapshot(casting = true))

        assertEquals(2, switches.castingNotes)
    }
}
