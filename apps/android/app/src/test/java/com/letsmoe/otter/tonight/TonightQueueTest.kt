package com.letsmoe.otter.tonight

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private class MemoryTonightStorage : TonightStorage {
    var stored: StoredTonight? = null

    override fun read() = stored

    override fun write(stored: StoredTonight) {
        this.stored = stored
    }
}

class TonightQueueTest {

    private val storage = MemoryTonightStorage()
    private var nowMillis = 1_000_000_000L
    private val queue = TonightQueue(storage) { nowMillis }
    private val film = TonightEntry("movie:1", "Heat")
    private val episodeThree = TonightEntry("episode:3", "Severance · S1:E3 Half Loop")
    private val episodeFour = TonightEntry("episode:4", "Severance · S1:E4 The You You Are")

    @Test
    fun titlesPlayInTheOrderTheyWereLinedUp() {
        queue.add(film)
        queue.add(episodeThree)
        queue.add(episodeFour)

        assertEquals(episodeThree, queue.nextAfter("movie:1"))
        assertEquals(episodeFour, queue.nextAfter("episode:3"))
        assertNull(queue.nextAfter("episode:4"))
        assertNull(queue.nextAfter("movie:99"))
    }

    @Test
    fun aTitleIsQueuedOnce() {
        queue.add(film)
        queue.add(film)

        assertEquals(listOf(film), queue.entries.value)
    }

    @Test
    fun theTitleCardSaysWhereTheTitleStands() {
        queue.add(film)
        assertNull(queue.positionOf("movie:1"))

        queue.add(episodeThree)
        queue.add(episodeFour)

        assertEquals("2 of 3 tonight", queue.positionOf("episode:3")?.label)
        assertEquals("1 of 3 tonight", queue.positionOf("movie:1")?.label)
        assertNull(queue.positionOf("movie:99"))
    }

    @Test
    fun removingAndClearingChangeTheOrder() {
        queue.add(film)
        queue.add(episodeThree)
        queue.add(episodeFour)

        queue.remove("episode:3")
        assertEquals(episodeFour, queue.nextAfter("movie:1"))

        queue.clear()
        assertTrue(queue.entries.value.isEmpty())
    }

    @Test
    fun theQueueIsKeptForTheEveningAndGoneTheNextDay() {
        queue.add(film)
        queue.add(episodeThree)

        nowMillis += 17 * 60 * 60 * 1000L
        assertEquals(listOf(film, episodeThree), TonightQueue(storage) { nowMillis }.entries.value)

        nowMillis += 2 * 60 * 60 * 1000L
        assertTrue(TonightQueue(storage) { nowMillis }.entries.value.isEmpty())
    }

    @Test
    fun entriesSurviveBeingWrittenAsLines() {
        val entry = TonightEntry("episode:3", "Odd\tlabel\nwith breaks")

        assertEquals(TonightEntry("episode:3", "Odd label with breaks"), entryOfLine(lineOfEntry(entry)))
        assertNull(entryOfLine("no-tab"))
        assertNull(entryOfLine("\tlabel"))
    }
}
