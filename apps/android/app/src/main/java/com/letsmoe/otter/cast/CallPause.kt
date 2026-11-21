package com.letsmoe.otter.cast

import android.content.Context
import android.media.AudioManager
import com.letsmoe.otter.AppScope
import com.letsmoe.otter.settings.PlaybackSettings
import com.letsmoe.otter.ui.player.PlayerViewModel
import com.letsmoe.otter.ui.player.TelevisionPlayback
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

private const val CHECK_MILLISECONDS = 1000L

enum class CallPauseAction { NONE, PAUSE, RESUME }

/**
 * Whether the phone's audio is in a call right now: a ringing phone, a phone call or an app's
 * voice or video call. Read from the audio mode, which needs no permission.
 */
fun isCallMode(audioMode: Int): Boolean {
    return audioMode == AudioManager.MODE_RINGTONE ||
        audioMode == AudioManager.MODE_IN_CALL ||
        audioMode == AudioManager.MODE_IN_COMMUNICATION
}

/**
 * Decides what a call does to the TV: it pauses when a call starts while it plays, and plays on
 * when the call ends, but only if the call was what paused it (not the viewer, before or during).
 */
class CallPauseTracker {

    private var inCall = false
    private var pausedByCall = false

    fun onCheck(callNow: Boolean, playing: Boolean): CallPauseAction {
        if (callNow == inCall) {
            return CallPauseAction.NONE
        }
        inCall = callNow
        if (callNow) {
            return onCallStarted(playing)
        }
        return onCallEnded(playing)
    }

    fun reset() {
        inCall = false
        pausedByCall = false
    }

    private fun onCallStarted(playing: Boolean): CallPauseAction {
        if (!playing) {
            return CallPauseAction.NONE
        }
        pausedByCall = true
        return CallPauseAction.PAUSE
    }

    private fun onCallEnded(playing: Boolean): CallPauseAction {
        val resume = pausedByCall && !playing
        pausedByCall = false
        if (resume) {
            return CallPauseAction.RESUME
        }
        return CallPauseAction.NONE
    }
}

/** Pauses what plays on the TV during phone calls and plays on afterwards, when the setting is on. */
object CallPause {

    fun initialize(context: Context) {
        val audioManager = context.getSystemService(AudioManager::class.java)
        AppScope.launch {
            TelevisionPlayback.player.collectLatest { playback ->
                if (playback != null) {
                    watchCalls(audioManager, playback)
                }
            }
        }
    }

    // Polled only while a title plays on a TV; the audio mode is a cheap read.
    private suspend fun watchCalls(audioManager: AudioManager, playback: PlayerViewModel) {
        val tracker = CallPauseTracker()
        while (true) {
            checkCall(audioManager, playback, tracker)
            delay(CHECK_MILLISECONDS)
        }
    }

    private fun checkCall(audioManager: AudioManager, playback: PlayerViewModel, tracker: CallPauseTracker) {
        if (!PlaybackSettings.pauseForCalls) {
            tracker.reset()
            return
        }
        val action = tracker.onCheck(isCallMode(audioManager.mode), playback.state.value.isPlaying)
        when (action) {
            CallPauseAction.PAUSE -> playback.setPlaying(false)
            CallPauseAction.RESUME -> playback.setPlaying(true)
            CallPauseAction.NONE -> Unit
        }
    }
}
