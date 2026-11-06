package com.letsmoe.otter.updates

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VersionsTest {

    @Test
    fun laterNumbersAreNewer() {
        assertTrue(isNewerVersion("v0.2.0", "0.1.0"))
        assertTrue(isNewerVersion("1.0.10", "1.0.9"))
        assertTrue(isNewerVersion("1.1", "1.0.5"))
    }

    @Test
    fun sameOrOlderIsNotNewer() {
        assertFalse(isNewerVersion("v0.1.0", "0.1.0"))
        assertFalse(isNewerVersion("0.1.0", "0.2.0"))
        assertFalse(isNewerVersion("1.0", "1.0.0"))
    }

    @Test
    fun releaseIsNewerThanItsPreRelease() {
        assertTrue(isNewerVersion("1.0.0", "1.0.0-beta.1"))
        assertFalse(isNewerVersion("1.0.0-beta.1", "1.0.0"))
        assertTrue(isNewerVersion("1.0.0-beta.2", "1.0.0-beta.1"))
    }
}
