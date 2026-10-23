package dev.neoworks.otter.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

/** When intros and recaps are jumped over without pressing the skip button. */
enum class AutoSkipIntro(val label: String) {
    OFF("Off"),
    TELEVISION("Only on TV"),
    ALWAYS("Always"),
}

/** Player preferences, saved on this device only. */
object PlaybackSettings {

    private const val PREFERENCES = "playback"
    private const val KEY_AUTO_SKIP_INTRO = "autoSkipIntro"

    private lateinit var preferences: SharedPreferences

    fun initialize(context: Context) {
        preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
    }

    var autoSkipIntro: AutoSkipIntro
        get() {
            val stored = preferences.getString(KEY_AUTO_SKIP_INTRO, null)
            return AutoSkipIntro.entries.firstOrNull { option -> option.name == stored } ?: AutoSkipIntro.ALWAYS
        }
        set(value) {
            preferences.edit { putString(KEY_AUTO_SKIP_INTRO, value.name) }
        }

    /** Whether to skip on its own, given whether a TV (DLNA or Chromecast) plays. */
    fun skipsIntroAutomatically(casting: Boolean): Boolean {
        return when (autoSkipIntro) {
            AutoSkipIntro.OFF -> false
            AutoSkipIntro.TELEVISION -> casting
            AutoSkipIntro.ALWAYS -> true
        }
    }
}
