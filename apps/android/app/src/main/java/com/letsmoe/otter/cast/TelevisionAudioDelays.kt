package com.letsmoe.otter.cast

import android.content.Context
import androidx.core.content.edit

private const val PREFERENCES = "television_audio_delays"

/** How far each DLNA TV's sound is moved against the picture (positive: later), by the TV's UDN. */
class TelevisionAudioDelays(context: Context) {

    private val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    fun delayMillis(rendererId: String): Int {
        return preferences.getInt(rendererId, 0)
    }

    fun save(rendererId: String, delayMillis: Int) {
        preferences.edit { putInt(rendererId, delayMillis) }
    }
}
