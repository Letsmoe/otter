package com.letsmoe.otter.cast

import org.junit.Assert.assertEquals
import org.junit.Test

class TelevisionFormatsTest {

    @Test
    fun parsesHttpGetEntriesWithTheirProfiles() {
        val formats = parseSinkProtocolInfo(
            "http-get:*:video/mp2t:DLNA.ORG_PN=AVC_TS_HD_EU_ISO;DLNA.ORG_OP=01, rtsp-rtp-udp:*:video/mp4:*,http-get:*:video/mp4:*",
        )
        assertEquals(listOf(TelevisionFormat("video/mp2t", "AVC_TS_HD_EU_ISO"), TelevisionFormat("video/mp4", null)), formats)
    }

    @Test
    fun keepsTheDefaultTypeWhenTheTelevisionListsItOrNothing() {
        val accepted = parseSinkProtocolInfo("http-get:*:video/mpeg:*,http-get:*:video/mp2t:*")
        assertEquals(AnnouncedFormat("video/mpeg", null), announcedFormat("video/mpeg", accepted))
        assertEquals(AnnouncedFormat("video/mpeg", null), announcedFormat("video/mpeg", emptyList()))
    }

    @Test
    fun switchesToAnAliasTheTelevisionAccepts() {
        val accepted = parseSinkProtocolInfo("http-get:*:video/vnd.dlna.mpeg-tts:*,http-get:*:video/MP2T:*")
        assertEquals(AnnouncedFormat("video/MP2T", null), announcedFormat("video/mpeg", accepted))
    }

    @Test
    fun namesAnH264ProfileWhenTheTelevisionOnlyTakesProfiles() {
        val accepted = parseSinkProtocolInfo(
            "http-get:*:video/mpeg:DLNA.ORG_PN=MPEG_PS_PAL," +
                "http-get:*:video/mpeg:DLNA.ORG_PN=AVC_TS_HD_EU_T," +
                "http-get:*:video/mpeg:DLNA.ORG_PN=AVC_TS_HD_EU_ISO",
        )
        assertEquals(AnnouncedFormat("video/mpeg", "AVC_TS_HD_EU_ISO"), announcedFormat("video/mpeg", accepted))
    }

    @Test
    fun leavesUnknownContainersAlone() {
        val accepted = parseSinkProtocolInfo("http-get:*:video/mp4:*")
        assertEquals(AnnouncedFormat("video/webm", null), announcedFormat("video/webm", accepted))
    }
}
