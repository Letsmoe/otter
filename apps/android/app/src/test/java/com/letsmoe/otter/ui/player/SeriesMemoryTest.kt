package com.letsmoe.otter.ui.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

private class MemorySeriesStorage : SeriesMemoryStorage {
    val speeds = mutableMapOf<String, Float>()
    val captions = mutableMapOf<String, CaptionChoice>()

    override fun readSpeed(seriesKey: String) = speeds[seriesKey]

    override fun writeSpeed(seriesKey: String, speed: Float) {
        speeds[seriesKey] = speed
    }

    override fun readCaptions(seriesKey: String) = captions[seriesKey]

    override fun writeCaptions(seriesKey: String, choice: CaptionChoice) {
        captions[seriesKey] = choice
    }
}

class SeriesMemoryTest {

    private val storage = MemorySeriesStorage()
    private val memory = SeriesMemory(storage)

    @Test
    fun aSeriesStartsWithTheSpeedAndCaptionsLastUsedForIt() {
        memory.open("profile:1", "series:1")
        memory.rememberSpeed(1.5f)
        memory.rememberCaptions(CaptionChoice(true, "de"))

        memory.open("profile:1", "series:1")

        assertEquals(1.5f, memory.speed)
        assertEquals(CaptionChoice(true, "de"), memory.captions)
    }

    @Test
    fun theLastCaptionsWinOverTheEarlierOnes() {
        memory.open("profile:1", "series:1")
        memory.rememberCaptions(CaptionChoice(true, "de"))
        memory.rememberCaptions(CaptionChoice(false, "de"))

        memory.open("profile:1", "series:1")

        assertEquals(CaptionChoice(false, "de"), memory.startingCaptions(null))
    }

    @Test
    fun otherSeriesAndProfilesStartWithoutIt() {
        memory.open("profile:1", "series:1")
        memory.rememberSpeed(2f)
        memory.rememberCaptions(CaptionChoice(true, "en"))

        memory.open("profile:1", "series:2")
        assertNull(memory.speed)
        assertNull(memory.captions)

        memory.open("profile:2", "series:1")
        assertNull(memory.speed)
        assertNull(memory.captions)
    }

    @Test
    fun aMovieRemembersNothing() {
        memory.open("profile:1", null)
        memory.rememberSpeed(2f)
        memory.rememberCaptions(CaptionChoice(true, "en"))

        assertNull(memory.speed)
        assertNull(memory.captions)
        assertEquals(emptyMap<String, Float>(), storage.speeds)
        assertEquals(emptyMap<String, CaptionChoice>(), storage.captions)
    }

    @Test
    fun withoutMemoryThereAreNoCaptionsToStartWithWhenNoProfileIsKnown() {
        memory.open("profile:1", "series:1")

        assertNull(memory.startingCaptions(null))
    }

    @Test
    fun aCaptionChoiceSurvivesBeingWrittenDown() {
        val choice = CaptionChoice(true, "pt-BR")

        assertEquals(choice, CaptionChoice.decode(choice.encode()))
        assertNull(CaptionChoice.decode("nonsense"))
        assertNull(CaptionChoice.decode(null))
    }
}
