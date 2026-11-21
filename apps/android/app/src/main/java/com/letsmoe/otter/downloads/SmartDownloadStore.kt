package com.letsmoe.otter.downloads

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

private const val DEFAULT_BUDGET_GIGABYTES = 10

/** The Smart Downloads setting and the bookkeeping of what it downloaded, saved on this device only. */
object SmartDownloadStore {

    private const val PREFERENCES = "smart_downloads"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_BUDGET_GIGABYTES = "budgetGigabytes"
    private const val KEY_QUALITY = "quality"
    private const val KEY_SMART_IDS = "smartIds"
    private const val KEY_REMOVED_MEDIA = "removedMedia"

    private lateinit var preferences: SharedPreferences

    private val mutableSmartIds = MutableStateFlow<Set<String>>(emptySet())
    /** The ids of the downloads that Smart Downloads started; the list labels them. */
    val smartIds: StateFlow<Set<String>> = mutableSmartIds

    fun initialize(context: Context) {
        preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        mutableSmartIds.value = preferences.getStringSet(KEY_SMART_IDS, emptySet()).orEmpty().toSet()
    }

    var enabled: Boolean
        get() = preferences.getBoolean(KEY_ENABLED, false)
        set(value) {
            preferences.edit { putBoolean(KEY_ENABLED, value) }
        }

    var budgetGigabytes: Int
        get() = preferences.getInt(KEY_BUDGET_GIGABYTES, DEFAULT_BUDGET_GIGABYTES)
        set(value) {
            preferences.edit { putInt(KEY_BUDGET_GIGABYTES, value) }
        }

    var quality: SmartQuality
        get() {
            val stored = preferences.getString(KEY_QUALITY, null)
            return SmartQuality.entries.firstOrNull { option -> option.name == stored } ?: SmartQuality.HD720
        }
        set(value) {
            preferences.edit { putString(KEY_QUALITY, value.name) }
        }

    fun isSmart(downloadId: String): Boolean = downloadId in mutableSmartIds.value

    fun markSmart(downloadId: String) {
        saveSmartIds(mutableSmartIds.value + downloadId)
    }

    fun forget(downloadId: String) {
        saveSmartIds(mutableSmartIds.value - downloadId)
    }

    /** Media the viewer deleted a smart download of: it isn't downloaded again by itself. */
    fun removedMedia(): Set<String> = preferences.getStringSet(KEY_REMOVED_MEDIA, emptySet()).orEmpty().toSet()

    /** The viewer deleted [downloadId]; when it was a smart one, [mediaId] is not downloaded again. */
    fun onRemovedByViewer(downloadId: String, mediaId: String) {
        if (!isSmart(downloadId)) {
            return
        }
        forget(downloadId)
        preferences.edit { putStringSet(KEY_REMOVED_MEDIA, removedMedia() + mediaId) }
    }

    private fun saveSmartIds(ids: Set<String>) {
        mutableSmartIds.value = ids
        preferences.edit { putStringSet(KEY_SMART_IDS, ids) }
    }
}
