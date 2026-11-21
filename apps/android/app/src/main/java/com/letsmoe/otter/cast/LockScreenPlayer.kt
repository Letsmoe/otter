package com.letsmoe.otter.cast

import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player
import com.letsmoe.otter.ui.player.TelevisionPlayback

// What the lock screen and the notification shade may do: play, pause and the TV's volume.
// Seeking from there would reload the TV's stream, so it stays in the app.
private val lockScreenCommandList = listOf(
    Player.COMMAND_PLAY_PAUSE,
    Player.COMMAND_GET_CURRENT_MEDIA_ITEM,
    Player.COMMAND_GET_METADATA,
    Player.COMMAND_GET_TIMELINE,
    Player.COMMAND_GET_DEVICE_VOLUME,
    Player.COMMAND_SET_DEVICE_VOLUME_WITH_FLAGS,
    Player.COMMAND_ADJUST_DEVICE_VOLUME_WITH_FLAGS,
)

/** The commands the lock screen may use, out of those the TV player [isAvailable]. */
fun lockScreenCommandCodes(isAvailable: (Int) -> Boolean): List<Int> {
    return lockScreenCommandList.filter(isAvailable)
}

private fun lockScreenCommands(available: Player.Commands): Player.Commands {
    val codes = lockScreenCommandCodes { command -> available.contains(command) }
    return Player.Commands.Builder().addAll(*codes.toIntArray()).build()
}

/**
 * The [television] as the media session sees it. Play and pause go through the title's player
 * (like the remote's button), so Watch Together and the app's state stay in step.
 */
class LockScreenPlayer(private val television: DlnaPlayer) : ForwardingPlayer(television) {

    override fun getAvailableCommands(): Player.Commands {
        return lockScreenCommands(super.getAvailableCommands())
    }

    override fun isCommandAvailable(command: Int): Boolean {
        return availableCommands.contains(command)
    }

    override fun play() {
        requestPlaying(true)
    }

    override fun pause() {
        requestPlaying(false)
    }

    override fun setPlayWhenReady(playWhenReady: Boolean) {
        requestPlaying(playWhenReady)
    }

    private fun requestPlaying(shouldPlay: Boolean) {
        val playback = TelevisionPlayback.player.value
        if (playback == null) {
            television.playWhenReady = shouldPlay
            return
        }
        playback.setPlaying(shouldPlay)
    }
}
