package com.letsmoe.otter.ui.title

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamListTest {

    @Test
    fun searchingWithoutLinksIsBusy() {
        val streams = StreamList(emptyList(), searching = true)
        assertTrue(streams.isBusy)
        assertFalse(streams.nothingFound)
    }

    @Test
    fun finishedWithoutLinksFoundNothing() {
        val streams = StreamList(emptyList(), searching = false)
        assertFalse(streams.isBusy)
        assertTrue(streams.nothingFound)
    }

    @Test
    fun missingVpnIsNeitherBusyNorEmpty() {
        val searching = StreamList(emptyList(), searching = true, vpnBlocked = true)
        val finished = StreamList(emptyList(), searching = false, vpnBlocked = true)
        assertFalse(searching.isBusy)
        assertFalse(finished.nothingFound)
    }
}
