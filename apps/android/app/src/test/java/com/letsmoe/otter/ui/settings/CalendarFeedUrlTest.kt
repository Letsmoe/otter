package com.letsmoe.otter.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class CalendarFeedUrlTest {

    @Test
    fun theFeedPathFollowsTheBackendsAddress() {
        assertEquals("http://192.168.1.10:8080/api/calendar/abc.ics", calendarFeedUrl("http://192.168.1.10:8080", "/api/calendar/abc.ics"))
    }

    @Test
    fun aTrailingSlashOfTheAddressIsNotDoubled() {
        assertEquals("http://nas.local:8080/api/calendar/abc.ics", calendarFeedUrl("http://nas.local:8080/", "/api/calendar/abc.ics"))
    }
}
