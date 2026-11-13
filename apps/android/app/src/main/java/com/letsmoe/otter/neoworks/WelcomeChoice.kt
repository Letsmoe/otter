package com.letsmoe.otter.neoworks

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Whether the first-start question (sign in with Neoworks or continue as a guest) was answered. */
object WelcomeChoice {

    private const val PREFERENCES = "welcome"
    private const val KEY_ANSWERED = "answered"

    private lateinit var preferences: SharedPreferences
    private val mutableAnswered = MutableStateFlow(true)
    val answered: StateFlow<Boolean> = mutableAnswered

    fun initialize(context: Context) {
        preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        mutableAnswered.value = preferences.getBoolean(KEY_ANSWERED, false)
    }

    fun markAnswered() {
        if (mutableAnswered.value) {
            return
        }
        preferences.edit { putBoolean(KEY_ANSWERED, true) }
        mutableAnswered.value = true
    }
}
