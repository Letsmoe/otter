package com.letsmoe.otter.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import java.time.LocalTime

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
    private const val KEY_TELEVISION_OVERLAYS = "televisionOverlays"
    private const val KEY_TELEVISION_TITLE_CARD = "televisionTitleCard"
    private const val KEY_EVEN_VOLUME = "evenVolume"
    private const val KEY_BRIGHTEN_DARK_SCENES = "brightenDarkScenes"
    private const val KEY_BEDTIME_MINUTES = "bedtimeMinutes"
    private const val KEY_NIGHT_VOLUME_LIMIT = "nightVolumeLimit"
    private const val NOT_STORED = -1

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

    /**
     * Whether captions and hints (skip intro, next episode) are drawn into the picture a DLNA TV
     * gets. The phone re-encodes the video for it, which costs battery.
     */
    var televisionOverlays: Boolean
        get() = preferences.getBoolean(KEY_TELEVISION_OVERLAYS, true)
        set(value) {
            preferences.edit { putBoolean(KEY_TELEVISION_OVERLAYS, value) }
        }

    /** Evens out loud and quiet passages, on the phone and in the sound the phone makes for a TV. */
    var evenVolume: Boolean
        get() = preferences.getBoolean(KEY_EVEN_VOLUME, false)
        set(value) {
            preferences.edit { putBoolean(KEY_EVEN_VOLUME, value) }
        }

    /** Minutes after midnight when the night starts; null when there is no bedtime. */
    var bedtimeMinutes: Int?
        get() {
            val stored = preferences.getInt(KEY_BEDTIME_MINUTES, NOT_STORED)
            if (stored == NOT_STORED) {
                return null
            }
            return stored
        }
        set(value) {
            preferences.edit {
                if (value == null) {
                    remove(KEY_BEDTIME_MINUTES)
                } else {
                    putInt(KEY_BEDTIME_MINUTES, value)
                }
            }
        }

    /** Whether the night after the bedtime has started right now. */
    fun isPastBedtime(now: LocalTime = LocalTime.now()): Boolean {
        val bedtime = bedtimeMinutes ?: return false
        return Bedtime.hasStarted(bedtime, now.hour * 60 + now.minute)
    }

    /** The loudest volume (0 to 100) the TV may have during the night; null for no limit. */
    var nightVolumeLimit: Int?
        get() {
            val stored = preferences.getInt(KEY_NIGHT_VOLUME_LIMIT, NOT_STORED)
            if (stored == NOT_STORED) {
                return null
            }
            return stored
        }
        set(value) {
            preferences.edit {
                if (value == null) {
                    remove(KEY_NIGHT_VOLUME_LIMIT)
                } else {
                    putInt(KEY_NIGHT_VOLUME_LIMIT, value)
                }
            }
        }

    /** The loudest volume (0 to 100) the TV may have right now. */
    val televisionVolumeMaximum: Int
        get() = NightVolume.maximum(nightVolumeLimit, isPastBedtime())

    /** Even volume that the bedtime switched on. Not saved: it ends with the night or when the viewer switches it off. */
    @Volatile
    var evenVolumeFromBedtime = false

    /** Even volume as it applies now: the setting, or the bedtime's. */
    val evenVolumeActive: Boolean
        get() = evenVolume || (evenVolumeFromBedtime && isPastBedtime())

    /** Lifts the shadows of the picture the phone makes for a TV, from its next stream on. */
    var brightenDarkScenes: Boolean
        get() = preferences.getBoolean(KEY_BRIGHTEN_DARK_SCENES, false)
        set(value) {
            preferences.edit { putBoolean(KEY_BRIGHTEN_DARK_SCENES, value) }
        }

    /** Whether a TV with drawn-in hints shows the title and episode for a few seconds when a title starts. */
    var televisionTitleCard: Boolean
        get() = preferences.getBoolean(KEY_TELEVISION_TITLE_CARD, true)
        set(value) {
            preferences.edit { putBoolean(KEY_TELEVISION_TITLE_CARD, value) }
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
