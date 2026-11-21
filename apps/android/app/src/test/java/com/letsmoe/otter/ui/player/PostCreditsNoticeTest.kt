package com.letsmoe.otter.ui.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PostCreditsNoticeTest {

    private val twoHours = 7200.0

    @Test
    fun theNoticeStartsWhereTheCreditsAreGuessedToStart() {
        val notice = postCreditsNoticeFor(twoHours)!!

        // 5% of two hours is 6 minutes of credits.
        assertEquals(twoHours - 360.0, notice.startSeconds, 0.001)
        assertEquals(notice.startSeconds + 20.0, notice.endSeconds, 0.001)
    }

    @Test
    fun theGuessedCreditsStayBetweenFourAndNineMinutes() {
        assertEquals(60 * 60.0 - 240.0, postCreditsNoticeFor(60 * 60.0)!!.startSeconds, 0.001)
        assertEquals(4 * 3600.0 - 540.0, postCreditsNoticeFor(4 * 3600.0)!!.startSeconds, 0.001)
    }

    @Test
    fun anUnknownOrShortLengthHasNoNotice() {
        assertNull(postCreditsNoticeFor(0.0))
        assertNull(postCreditsNoticeFor(Double.NaN))
        assertNull(postCreditsNoticeFor(Double.POSITIVE_INFINITY))
        assertNull(postCreditsNoticeFor(10 * 60.0))
    }

    @Test
    fun theNoticeShowsOnlyForFilmsWithASceneAndOnlyWhileTheCreditsStart() {
        val start = twoHours - 360.0

        assertTrue(showsPostCreditsNotice(true, start + 1, twoHours))
        assertFalse(showsPostCreditsNotice(true, start - 1, twoHours))
        assertFalse(showsPostCreditsNotice(true, start + 20, twoHours))
        assertFalse(showsPostCreditsNotice(false, start + 1, twoHours))
        assertFalse(showsPostCreditsNotice(true, start + 1, 0.0))
    }
}
