package com.letsmoe.otter.ui.player

import android.content.Context
import androidx.core.content.edit

/** Where the skip habits of each profile's series are kept. */
interface SkipHabitStorage {
    fun readIntroHabit(seriesKey: String): IntroHabit

    fun writeIntroHabit(seriesKey: String, habit: IntroHabit)

    fun readFinishedEpisode(seriesKey: String): FinishedEpisode?

    fun writeFinishedEpisode(seriesKey: String, episode: FinishedEpisode)
}

/** Saved on this device only, like the other player preferences. */
class PreferencesSkipHabitStorage(context: Context) : SkipHabitStorage {

    private val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    override fun readIntroHabit(seriesKey: String): IntroHabit {
        return IntroHabit.decode(preferences.getString("$seriesKey|intro", null))
    }

    override fun writeIntroHabit(seriesKey: String, habit: IntroHabit) {
        preferences.edit { putString("$seriesKey|intro", habit.encode()) }
    }

    override fun readFinishedEpisode(seriesKey: String): FinishedEpisode? {
        return FinishedEpisode.decode(preferences.getString("$seriesKey|finished", null))
    }

    override fun writeFinishedEpisode(seriesKey: String, episode: FinishedEpisode) {
        preferences.edit { putString("$seriesKey|finished", episode.encode()) }
    }

    private companion object {
        const val PREFERENCES = "skip-habits"
    }
}
