package com.letsmoe.otter.profiles

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.letsmoe.otter.graphql.fragment.ProfileFields
import com.letsmoe.otter.graphql.type.SubtitleSize
import com.letsmoe.otter.graphql.type.SubtitleStyle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** The profile that's watching; kept across launches until it logs out. */
object ProfileSession {

    private const val PREFERENCES = "active-profile"
    private const val KEY_ID = "id"
    private const val KEY_NAME = "name"
    private const val KEY_AVATAR_URL = "avatarUrl"
    private const val KEY_HAS_PIN = "hasPin"
    private const val KEY_IS_CHILD = "isChild"
    private const val KEY_LANGUAGES = "languages"
    private const val KEY_SUBTITLES_ENABLED = "subtitlesEnabled"
    private const val KEY_SUBTITLE_LANGUAGE = "subtitleLanguage"
    private const val KEY_SUBTITLE_SIZE = "subtitleSize"
    private const val KEY_SUBTITLE_STYLE = "subtitleStyle"
    private const val KEY_VOICE_BOOST = "voiceBoost"
    private const val KEY_AGE_LIMIT = "ageLimit"
    private const val KEY_BEDTIME_MINUTES = "bedtimeMinutes"
    private const val NO_BEDTIME = -1
    private const val KEY_SIMPLE_MODE = "simpleMode"

    private lateinit var preferences: SharedPreferences
    private val mutableActiveProfile = MutableStateFlow<ProfileFields?>(null)
    val activeProfile: StateFlow<ProfileFields?> = mutableActiveProfile

    fun initialize(context: Context) {
        preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        mutableActiveProfile.value = readStoredProfile()
    }

    fun login(profile: ProfileFields) {
        preferences.edit {
            putString(KEY_ID, profile.id)
            putString(KEY_NAME, profile.name)
            putString(KEY_AVATAR_URL, profile.avatarUrl)
            putBoolean(KEY_HAS_PIN, profile.hasPin)
            putBoolean(KEY_IS_CHILD, profile.isChild)
            putString(KEY_LANGUAGES, profile.languages.joinToString(","))
            putBoolean(KEY_SUBTITLES_ENABLED, profile.subtitlesEnabled)
            putString(KEY_SUBTITLE_LANGUAGE, profile.subtitleLanguage)
            putString(KEY_SUBTITLE_SIZE, profile.subtitleSize.rawValue)
            putString(KEY_SUBTITLE_STYLE, profile.subtitleStyle.rawValue)
            putBoolean(KEY_VOICE_BOOST, profile.voiceBoost)
            putInt(KEY_AGE_LIMIT, profile.ageLimit)
            putInt(KEY_BEDTIME_MINUTES, storedBedtime(profile.bedtimeMinutes))
            putBoolean(KEY_SIMPLE_MODE, profile.simpleMode)
        }
        mutableActiveProfile.value = profile
    }

    fun logout() {
        preferences.edit { clear() }
        mutableActiveProfile.value = null
    }

    private fun readStoredProfile(): ProfileFields? {
        val id = preferences.getString(KEY_ID, null) ?: return null
        return ProfileFields(
            id = id,
            name = preferences.getString(KEY_NAME, null).orEmpty(),
            avatarUrl = preferences.getString(KEY_AVATAR_URL, null),
            hasPin = preferences.getBoolean(KEY_HAS_PIN, false),
            isChild = preferences.getBoolean(KEY_IS_CHILD, false),
            languages = readStoredLanguages(),
            subtitlesEnabled = preferences.getBoolean(KEY_SUBTITLES_ENABLED, false),
            subtitleLanguage = preferences.getString(KEY_SUBTITLE_LANGUAGE, null),
            subtitleSize = readStoredSubtitleSize(),
            subtitleStyle = readStoredSubtitleStyle(),
            voiceBoost = preferences.getBoolean(KEY_VOICE_BOOST, false),
            ageLimit = preferences.getInt(KEY_AGE_LIMIT, 0),
            bedtimeMinutes = readStoredBedtime(),
            simpleMode = preferences.getBoolean(KEY_SIMPLE_MODE, false),
        )
    }

    private fun storedBedtime(bedtimeMinutes: Int?): Int {
        if (bedtimeMinutes == null) {
            return NO_BEDTIME
        }
        return bedtimeMinutes
    }

    private fun readStoredBedtime(): Int? {
        val stored = preferences.getInt(KEY_BEDTIME_MINUTES, NO_BEDTIME)
        if (stored == NO_BEDTIME) {
            return null
        }
        return stored
    }

    // Stored before languages existed: the default until the next login.
    private fun readStoredLanguages(): List<String> {
        val stored = preferences.getString(KEY_LANGUAGES, null)
        if (stored.isNullOrEmpty()) {
            return DEFAULT_LANGUAGES
        }
        return stored.split(",")
    }

    // Stored before captions existed: the default until the next login.
    private fun readStoredSubtitleSize(): SubtitleSize {
        val stored = preferences.getString(KEY_SUBTITLE_SIZE, null)
        if (stored == null) {
            return SubtitleSize.MEDIUM
        }
        return SubtitleSize.safeValueOf(stored)
    }

    // Stored before caption styles existed: the outline until the next login.
    private fun readStoredSubtitleStyle(): SubtitleStyle {
        val stored = preferences.getString(KEY_SUBTITLE_STYLE, null)
        if (stored == null) {
            return SubtitleStyle.OUTLINE
        }
        return SubtitleStyle.safeValueOf(stored)
    }

    /** Takes over an edit of the active profile, e.g. new languages. */
    fun refreshIfActive(profile: ProfileFields) {
        if (activeProfile.value?.id == profile.id) {
            login(profile)
        }
    }
}
