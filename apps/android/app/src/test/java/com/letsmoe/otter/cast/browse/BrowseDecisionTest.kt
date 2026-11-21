package com.letsmoe.otter.cast.browse

import org.junit.Assert.assertEquals
import org.junit.Test

class BrowseDecisionTest {

    private val severance = BrowsePreview("series:95396", "Severance", listOf("2022"), null, null)
    private val heat = BrowsePreview("movie:949", "Heat", listOf("1995"), null, null)

    private val browsing = BrowseInputs(severance, televisionId = "tv-1", playing = false, enabled = true, encoderUsable = true)

    @Test
    fun aNewTitleIsShownOnTheTelevision() {
        assertEquals(BrowseAction.Show(severance), nextBrowseAction(browsing, shown = null))
    }

    @Test
    fun theTitleAlreadyShownIsNotLoadedAgain() {
        val shown = ShownPreview("tv-1", severance)

        assertEquals(BrowseAction.Keep, nextBrowseAction(browsing, shown))
    }

    @Test
    fun anotherTitleReplacesTheOneShown() {
        val shown = ShownPreview("tv-1", heat)

        assertEquals(BrowseAction.Show(severance), nextBrowseAction(browsing, shown))
    }

    @Test
    fun aTitleShownOnAnotherTelevisionIsShownAgain() {
        val shown = ShownPreview("tv-2", severance)

        assertEquals(BrowseAction.Show(severance), nextBrowseAction(browsing, shown))
    }

    @Test
    fun leavingTheTitlePageKeepsWhatTheTelevisionShows() {
        val shown = ShownPreview("tv-1", severance)

        assertEquals(BrowseAction.Keep, nextBrowseAction(browsing.copy(preview = null), shown))
    }

    @Test
    fun aPlayingTitleOwnsTheTelevision() {
        val shown = ShownPreview("tv-1", severance)

        assertEquals(BrowseAction.Forget, nextBrowseAction(browsing.copy(playing = true), shown))
    }

    @Test
    fun nothingIsShownWithoutADlnaTelevision() {
        assertEquals(BrowseAction.Forget, nextBrowseAction(browsing.copy(televisionId = null), ShownPreview("tv-1", severance)))
    }

    @Test
    fun switchingTheSettingOffTakesThePictureOffTheTelevision() {
        val off = browsing.copy(enabled = false)

        assertEquals(BrowseAction.Clear, nextBrowseAction(off, ShownPreview("tv-1", severance)))
        assertEquals(BrowseAction.Keep, nextBrowseAction(off, shown = null))
    }

    @Test
    fun aFailedEncoderShowsNothingNew() {
        assertEquals(BrowseAction.Keep, nextBrowseAction(browsing.copy(encoderUsable = false), shown = null))
    }
}
