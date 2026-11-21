package com.letsmoe.otter.cast

import android.app.PendingIntent
import android.content.Context
import android.os.Bundle
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.letsmoe.otter.ui.player.TelevisionPlayback

private const val REPLAY_ACTION = "com.letsmoe.otter.REPLAY_WITH_CAPTIONS"
private const val SESSION_ID = "otter-television"

/**
 * A media session around the TV that plays: the lock screen and the notification shade show
 * the title with pause/play, "Replay with captions" and, through the session's remote volume,
 * the phone's volume buttons set the TV's volume. All without unlocking the phone.
 */
class LockScreenControls(context: Context, val television: DlnaPlayer, openApp: PendingIntent) {

    private val replayCommand = SessionCommand(REPLAY_ACTION, Bundle.EMPTY)

    val player = LockScreenPlayer(television)

    val session: MediaSession = MediaSession.Builder(context, player)
        .setId(SESSION_ID)
        .setSessionActivity(openApp)
        .setCallback(SessionCallback())
        .setMediaButtonPreferences(listOf(replayButton()))
        .build()

    fun replayWithCaptions() {
        TelevisionPlayback.player.value?.replayWithCaptions()
    }

    fun togglePlay() {
        TelevisionPlayback.player.value?.togglePlay()
    }

    fun release() {
        session.release()
    }

    // Skip back 10 seconds is what the replay does, with captions on top.
    private fun replayButton(): CommandButton {
        return CommandButton.Builder(CommandButton.ICON_SKIP_BACK_10)
            .setSessionCommand(replayCommand)
            .setDisplayName("Replay with captions")
            .build()
    }

    private inner class SessionCallback : MediaSession.Callback {

        override fun onConnect(session: MediaSession, controller: MediaSession.ControllerInfo): MediaSession.ConnectionResult {
            val sessionCommands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon().add(replayCommand).build()
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(sessionCommands)
                .build()
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle,
        ): ListenableFuture<SessionResult> {
            if (customCommand.customAction != REPLAY_ACTION) {
                return Futures.immediateFuture(SessionResult(SessionResult.RESULT_ERROR_NOT_SUPPORTED))
            }
            replayWithCaptions()
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }
    }
}
