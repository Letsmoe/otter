package com.letsmoe.otter.cast

import androidx.media3.common.Player
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LockScreenCommandsTest {

    private val televisionCommands = setOf(
        Player.COMMAND_PLAY_PAUSE,
        Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM,
        Player.COMMAND_SEEK_FORWARD,
        Player.COMMAND_STOP,
        Player.COMMAND_GET_TIMELINE,
    )

    private fun allowedFor(available: Set<Int>): List<Int> {
        return lockScreenCommandCodes { command -> command in available }
    }

    @Test
    fun playAndPauseStayOnTheLockScreen() {
        val commands = allowedFor(televisionCommands)

        assertTrue(Player.COMMAND_PLAY_PAUSE in commands)
        assertTrue(Player.COMMAND_GET_TIMELINE in commands)
    }

    @Test
    fun seekingAndStoppingStayInTheApp() {
        val commands = allowedFor(televisionCommands)

        assertFalse(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM in commands)
        assertFalse(Player.COMMAND_SEEK_FORWARD in commands)
        assertFalse(Player.COMMAND_STOP in commands)
    }

    @Test
    fun theVolumeIsOfferedOnlyWhenTheTelevisionHasOne() {
        assertFalse(Player.COMMAND_GET_DEVICE_VOLUME in allowedFor(televisionCommands))

        val withVolume = televisionCommands + Player.COMMAND_GET_DEVICE_VOLUME + Player.COMMAND_ADJUST_DEVICE_VOLUME_WITH_FLAGS
        val commands = allowedFor(withVolume)

        assertTrue(Player.COMMAND_GET_DEVICE_VOLUME in commands)
        assertTrue(Player.COMMAND_ADJUST_DEVICE_VOLUME_WITH_FLAGS in commands)
        assertFalse(Player.COMMAND_SET_DEVICE_VOLUME_WITH_FLAGS in commands)
    }
}
