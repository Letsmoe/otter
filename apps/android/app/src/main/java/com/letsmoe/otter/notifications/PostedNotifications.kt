package com.letsmoe.otter.notifications

import android.content.Context

private const val PREFERENCES = "posted_notifications"

/** Which backend notifications already reached the notification shade, per profile. */
object PostedNotifications {

    /**
     * The items of [newest] not posted before, and remembers them as posted. The first check of a
     * profile only remembers: news from before notifications were on isn't news anymore.
     */
    fun takeUnposted(context: Context, profileId: String, newest: List<NotificationItem>): List<NotificationItem> {
        val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        val known = preferences.getStringSet(profileId, null)
        val currentIds = newest.map { item -> item.id }.toSet()
        preferences.edit().putStringSet(profileId, currentIds).apply()
        if (known == null) {
            return emptyList()
        }
        return newest.filter { item -> item.id !in known }
    }
}
