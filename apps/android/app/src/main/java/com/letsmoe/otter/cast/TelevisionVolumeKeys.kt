package com.letsmoe.otter.cast

import android.os.SystemClock
import android.view.KeyEvent
import com.letsmoe.otter.AppScope
import com.letsmoe.otter.ui.player.TelevisionPlayback
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

// How much one press changes the TV's volume (0 to 100); a held key repeats.
private const val VOLUME_STEP = 3
private const val MAX_VOLUME = 100
// The TV's volume is read again when the last press is older than this: its own remote may have changed it.
private const val KNOWN_VOLUME_MILLISECONDS = 5000L

/** The TV's volume after a press: [direction] is +1 for volume up, -1 for volume down. */
fun volumeAfterPress(current: Int, direction: Int): Int {
    return (current + direction * VOLUME_STEP).coerceIn(0, MAX_VOLUME)
}

/** A volume the phone's buttons just set on the TV; [id] tells equal volumes apart for the on-screen display. */
data class TelevisionVolumeChange(val volume: Int, val id: Long)

/**
 * The phone's volume buttons set the TV's volume while a title plays on a DLNA TV that has a volume
 * control. Anywhere else the buttons keep adjusting the phone, since [handles] is false.
 */
object TelevisionVolumeKeys {

    private val mutableChange = MutableStateFlow<TelevisionVolumeChange?>(null)
    /** The latest change, for the on-screen display. */
    val change: StateFlow<TelevisionVolumeChange?> = mutableChange

    private val adjusting = Mutex()
    private var knownVolume: Int? = null
    private var knownFor: DlnaPlayer? = null
    private var knownAtMilliseconds = 0L
    private var changeCount = 0L

    /** Whether a volume button should go to the TV right now instead of to the phone. */
    fun handles(keyCode: Int): Boolean {
        if (keyCode != KeyEvent.KEYCODE_VOLUME_UP && keyCode != KeyEvent.KEYCODE_VOLUME_DOWN) {
            return false
        }
        return controlledTelevision() != null
    }

    fun press(keyCode: Int) {
        val television = controlledTelevision() ?: return
        var direction = 1
        if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            direction = -1
        }
        AppScope.launch { adjust(television, direction) }
    }

    private fun controlledTelevision(): DlnaPlayer? {
        val target = TelevisionConnection.target.value as? TelevisionTarget.Dlna ?: return null
        if (TelevisionPlayback.player.value == null || !target.player.supportsVolume) {
            return null
        }
        return target.player
    }

    // One press at a time: the first needs the TV's volume to start from, the next ones use the new one.
    private suspend fun adjust(television: DlnaPlayer, direction: Int) {
        adjusting.withLock {
            val current = currentVolume(television) ?: return
            val target = volumeAfterPress(current, direction)
            knownVolume = target
            knownFor = television
            knownAtMilliseconds = SystemClock.elapsedRealtime()
            television.changeVolume(target)
            changeCount += 1
            mutableChange.value = TelevisionVolumeChange(target, changeCount)
        }
    }

    private suspend fun currentVolume(television: DlnaPlayer): Int? {
        val known = knownVolume
        val isFresh = SystemClock.elapsedRealtime() - knownAtMilliseconds < KNOWN_VOLUME_MILLISECONDS
        if (known != null && isFresh && knownFor === television) {
            return known
        }
        return television.readVolume()
    }
}
