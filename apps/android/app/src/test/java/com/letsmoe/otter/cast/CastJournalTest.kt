package com.letsmoe.otter.cast

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CastJournalTest {

    private val renderer = DlnaRenderer("Living room", "uuid:tv", "http://tv/control", "urn:schemas-upnp-org:service:AVTransport:1")

    private fun position(relTime: String, duration: String): SoapExchange {
        val response = "<RelTime>$relTime</RelTime><TrackDuration>$duration</TrackDuration>"
        return SoapExchange("GetPositionInfo", "<request/>", 200, response, null, 12)
    }

    @Test
    fun pollsAreOnlyRecordedWhenMoreThanTheClockChanged() {
        val journal = CastJournal(renderer)
        journal.record(position("0:00:01", "1:30:00"))
        journal.record(position("0:00:02", "1:30:00"))
        journal.record(position("0:00:03", "0:00:00"))
        assertEquals(2, journal.text().split("\n\n").size)
    }

    @Test
    fun commandsAreAlwaysRecordedWithWhatTheTelevisionAnswered() {
        val journal = CastJournal(renderer)
        val fault = "<errorCode>714</errorCode><errorDescription>Illegal MIME-type</errorDescription>"
        journal.record(SoapExchange("SetAVTransportURI", "<CurrentURI>http://phone/stream</CurrentURI>", 500, fault, null, 80))
        journal.record(SoapExchange("Play", "<Speed>1</Speed>", null, "", "java.net.SocketTimeoutException", 30000))
        val text = journal.text()
        assertTrue(text.contains("SetAVTransportURI → HTTP 500"))
        assertTrue(text.contains("Illegal MIME-type"))
        assertTrue(text.contains("Play → java.net.SocketTimeoutException"))
    }

    @Test
    fun aNewCastStartsEmptyWithANewId() {
        val journal = CastJournal(renderer)
        journal.note("TV accepts 3 formats")
        val firstCastId = journal.castId
        journal.startCast()
        assertNotEquals(firstCastId, journal.castId)
        assertEquals("", journal.text())
    }
}
