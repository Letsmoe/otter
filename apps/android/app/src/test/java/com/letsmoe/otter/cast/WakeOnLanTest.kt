package com.letsmoe.otter.cast

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class WakeOnLanTest {

    private val address = byteArrayOf(0x00, 0x1A, 0x2B, 0x3C, 0x4D, 0x5E)

    @Test
    fun theMagicPacketIsSixOnesAndSixteenTimesTheAddress() {
        val packet = magicPacket(address)

        assertEquals(102, packet.size)
        assertArrayEquals(ByteArray(6) { 0xFF.toByte() }, packet.copyOfRange(0, 6))
        for (repeat in 1..16) {
            assertArrayEquals(address, packet.copyOfRange(repeat * 6, repeat * 6 + 6))
        }
    }

    @Test
    fun addressesAreReadWithColonsDashesOrNoSeparator() {
        assertArrayEquals(address, parseMacAddress("00:1a:2b:3c:4d:5e"))
        assertArrayEquals(address, parseMacAddress(" 00-1A-2B-3C-4D-5E "))
        assertArrayEquals(address, parseMacAddress("001A2B3C4D5E"))
    }

    @Test
    fun somethingThatIsNoAddressIsRefused() {
        assertNull(parseMacAddress("00:1a:2b:3c:4d"))
        assertNull(parseMacAddress("00:1a:2b:3c:4d:5e:6f"))
        assertNull(parseMacAddress("00:1a-2b:3c-4d:5e"))
        assertNull(parseMacAddress("zz:1a:2b:3c:4d:5e"))
        assertNull(parseMacAddress(""))
    }

    @Test
    fun anAddressIsShownInCapitals() {
        assertEquals("00:1A:2B:3C:4D:5E", formatMacAddress(address))
    }

    @Test
    fun aDeviceDescriptionMayNameTheAddress() {
        val description = "<device><friendlyName>TV</friendlyName><macAddress>d4-6a-6a-01-02-03</macAddress></device>"

        assertEquals("D4:6A:6A:01:02:03", findMacAddress(description))
    }

    @Test
    fun placeholdersAndLongerHexRunsAreNotAddresses() {
        assertNull(findMacAddress("<mac>00:00:00:00:00:00</mac>"))
        assertNull(findMacAddress("<mac>FF:FF:FF:FF:FF:FF</mac>"))
        assertNull(findMacAddress("<serial>aa:bb:cc:dd:ee:ff:00:11</serial>"))
        assertNull(findMacAddress("<device>nothing here</device>"))
    }

    @Test
    fun theBroadcastAddressFillsTheHostBits() {
        val host = byteArrayOf(192.toByte(), 168.toByte(), 1, 23)

        assertArrayEquals(byteArrayOf(192.toByte(), 168.toByte(), 1, 255.toByte()), directedBroadcastAddress(host, 24))
        assertArrayEquals(byteArrayOf(192.toByte(), 168.toByte(), 255.toByte(), 255.toByte()), directedBroadcastAddress(host, 16))
        assertArrayEquals(byteArrayOf(192.toByte(), 168.toByte(), 1, 31), directedBroadcastAddress(host, 27))
    }

    @Test
    fun aTelevisionSurvivesBeingSavedWithoutItsOptionalServices() {
        val renderer = DlnaRenderer("Living room", "uuid:1", "http://tv/av", "urn:av:1")
        val saved = encodeWakeableTelevision(WakeableTelevision("00:1A:2B:3C:4D:5E", renderer))

        val loaded = decodeWakeableTelevision(saved)

        assertNotNull(loaded)
        assertEquals("00:1A:2B:3C:4D:5E", loaded?.macAddress)
        assertEquals(renderer, loaded?.renderer)
    }

    @Test
    fun aTelevisionSurvivesBeingSavedWithItsServices() {
        val renderer = DlnaRenderer(
            name = "Living room",
            id = "uuid:1",
            controlUrl = "http://tv/av",
            serviceType = "urn:av:1",
            volumeControl = DlnaService("http://tv/volume", "urn:rc:1"),
            connectionManager = DlnaService("http://tv/cm", "urn:cm:1"),
        )

        val loaded = decodeWakeableTelevision(encodeWakeableTelevision(WakeableTelevision("00:1A:2B:3C:4D:5E", renderer)))

        assertEquals(renderer, loaded?.renderer)
    }

    @Test
    fun aBrokenSavedTelevisionIsIgnored() {
        assertNull(decodeWakeableTelevision("only\tthree\tfields"))
    }
}
