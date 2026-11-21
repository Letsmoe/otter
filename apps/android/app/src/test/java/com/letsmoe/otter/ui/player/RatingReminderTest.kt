package com.letsmoe.otter.ui.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RatingReminderTest {

    private val reminder = RatingReminder()

    @Test
    fun theReminderIsTheLastTenSecondsOfTheFile() {
        val window = ratingReminderWindowFor(7200.0)!!

        assertEquals(7190.0, window.startSeconds, 0.001)
        assertEquals(7200.0, window.endSeconds, 0.001)
    }

    @Test
    fun anUnknownLengthHasNoWindow() {
        assertNull(ratingReminderWindowFor(0.0))
        assertNull(ratingReminderWindowFor(Double.NaN))
        assertNull(ratingReminderWindowFor(5.0))
    }

    @Test
    fun aFilmNotRatedYetAsksInTheLastSecondsAndAfterTheEnd() {
        reminder.open(rateable = true, unrated = true)

        assertFalse(reminder.shows(3000.0, 7200.0, ended = false))
        assertTrue(reminder.shows(7195.0, 7200.0, ended = false))
        assertTrue(reminder.shows(7200.0, 7200.0, ended = true))
        assertTrue(reminder.asksForRating)
    }

    @Test
    fun aRatedTitleOrAnEpisodeInTheMiddleOfASeriesAsksNothing() {
        reminder.open(rateable = true, unrated = false)
        assertFalse(reminder.shows(7195.0, 7200.0, ended = true))
        assertFalse(reminder.asksForRating)

        reminder.open(rateable = false, unrated = true)
        assertFalse(reminder.shows(2590.0, 2600.0, ended = true))
        assertFalse(reminder.asksForRating)
    }

    @Test
    fun aTitleOnlyAsksOnceItsRatingIsKnown() {
        assertFalse(reminder.shows(7195.0, 7200.0, ended = true))

        reminder.open(rateable = true, unrated = true)
        reminder.reset()

        assertFalse(reminder.shows(7195.0, 7200.0, ended = true))
    }
}
