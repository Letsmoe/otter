package com.letsmoe.otter.updates

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateChannelTest {

    private fun release(version: String, build: Int?, channel: UpdateChannel = UpdateChannel.RELEASE): AppRelease {
        return AppRelease(version, build, "https://github.com/otter.apk", 0, channel)
    }

    @Test
    fun buildNumbersComeFromTheApkName() {
        assertEquals(57, buildNumberOf("otter-v0.0.4-build57.apk"))
        assertEquals(61, buildNumberOf("otter-nightly-build61.apk"))
        assertNull(buildNumberOf("otter-v0.0.3.apk"))
    }

    @Test
    fun knownBuildsCompareByBuildNumber() {
        assertTrue(isNewerThanInstalled(release("nightly", 61, UpdateChannel.NIGHTLY), 57, "0.0.4"))
        assertFalse(isNewerThanInstalled(release("v0.0.5", 50), 57, "0.0.4"))
    }

    @Test
    fun olderReleasesWithoutABuildNumberCompareByVersion() {
        assertTrue(isNewerThanInstalled(release("v0.0.4", null), 57, "0.0.3"))
        assertFalse(isNewerThanInstalled(release("nightly", null, UpdateChannel.NIGHTLY), 57, "0.0.3"))
    }

    @Test
    fun localBuildsCompareByVersion() {
        assertTrue(isNewerThanInstalled(release("v0.0.4", 57), 0, "0.0.3"))
        assertFalse(isNewerThanInstalled(release("v0.0.3", 57), 0, "0.0.3"))
    }

    @Test
    fun onlyTheSameBuildCountsAsInstalled() {
        assertTrue(isInstalled(release("v0.0.4", 57), 57))
        assertFalse(isInstalled(release("v0.0.4", 50), 57))
        assertFalse(isInstalled(release("v0.0.3", null), 57))
    }

    @Test
    fun nightlyBuildsAreNamedByTheirBuild() {
        assertEquals("nightly build 61", release("nightly", 61, UpdateChannel.NIGHTLY).displayName())
        assertEquals("0.0.4", release("v0.0.4", 57).displayName())
    }
}
