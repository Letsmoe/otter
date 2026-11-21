package com.letsmoe.otter.ui.player

import android.content.Context
import androidx.core.content.edit
import com.letsmoe.otter.graphql.fragment.ProfileFields
import com.letsmoe.otter.profiles.captionLanguage

/** Whether captions show and in which language, as a series or a profile asks for them. */
data class CaptionChoice(val enabled: Boolean, val language: String) {

    fun encode(): String = "$enabled:$language"

    companion object {
        /** The choice [encode] wrote; null for anything else. */
        fun decode(stored: String?): CaptionChoice? {
            val parts = stored?.split(":", limit = 2) ?: return null
            if (parts.size != 2) {
                return null
            }
            return CaptionChoice(parts[0] == "true", parts[1])
        }
    }
}

/** What a profile's captions start with. */
fun ProfileFields.captionChoice(): CaptionChoice = CaptionChoice(subtitlesEnabled, captionLanguage())

/** Where the speed and captions last used for each profile's series are kept. */
interface SeriesMemoryStorage {
    fun readSpeed(seriesKey: String): Float?

    fun writeSpeed(seriesKey: String, speed: Float)

    fun readCaptions(seriesKey: String): CaptionChoice?

    fun writeCaptions(seriesKey: String, choice: CaptionChoice)
}

/** Saved on this device only, like the other player preferences. */
class PreferencesSeriesMemoryStorage(context: Context) : SeriesMemoryStorage {

    private val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    override fun readSpeed(seriesKey: String): Float? {
        val key = "$seriesKey|speed"
        if (!preferences.contains(key)) {
            return null
        }
        return preferences.getFloat(key, 1f)
    }

    override fun writeSpeed(seriesKey: String, speed: Float) {
        preferences.edit { putFloat("$seriesKey|speed", speed) }
    }

    override fun readCaptions(seriesKey: String): CaptionChoice? {
        return CaptionChoice.decode(preferences.getString("$seriesKey|captions", null))
    }

    override fun writeCaptions(seriesKey: String, choice: CaptionChoice) {
        preferences.edit { putString("$seriesKey|captions", choice.encode()) }
    }

    private companion object {
        const val PREFERENCES = "series-memory"
    }
}

/**
 * The speed and captions last used for the series of the playing episode, per profile. A series
 * starts with them; without any, the profile's own captions apply. Movies have no memory.
 */
class SeriesMemory(private val storage: SeriesMemoryStorage) {

    private var seriesKey: String? = null

    /** The speed last used for the series; null when none was. */
    var speed: Float? = null
        private set

    /** The captions last used for the series; null when none were. */
    var captions: CaptionChoice? = null
        private set

    fun reset() {
        seriesKey = null
        speed = null
        captions = null
    }

    /** Reads what [profileId] last used for [seriesId]; nothing for a movie. */
    fun open(profileId: String, seriesId: String?) {
        reset()
        if (seriesId == null) {
            return
        }
        val key = "$profileId|$seriesId"
        seriesKey = key
        speed = storage.readSpeed(key)
        captions = storage.readCaptions(key)
    }

    fun rememberSpeed(chosenSpeed: Float) {
        val key = seriesKey ?: return
        speed = chosenSpeed
        storage.writeSpeed(key, chosenSpeed)
    }

    fun rememberCaptions(choice: CaptionChoice) {
        val key = seriesKey ?: return
        captions = choice
        storage.writeCaptions(key, choice)
    }

    /** The captions a title starts with: the series', else the [profile]'s. */
    fun startingCaptions(profile: ProfileFields?): CaptionChoice? {
        val remembered = captions
        if (remembered != null) {
            return remembered
        }
        return profile?.captionChoice()
    }
}
