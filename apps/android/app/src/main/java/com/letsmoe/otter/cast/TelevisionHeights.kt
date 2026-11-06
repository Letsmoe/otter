package com.letsmoe.otter.cast

import android.content.Context

private const val PREFERENCES = "television_heights"

/**
 * The lowest video height each DLNA TV refused to play, by its UDN. UPnP doesn't say what a
 * TV can decode, so a TV is offered its best link once and learns its limit from a refusal;
 * a later link at or above that height that plays lifts the limit again.
 */
class TelevisionHeights(context: Context) {

    private val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    /** The lowest height the TV refused; null when it never refused one. */
    fun refusedHeight(rendererId: String): Int? {
        val height = preferences.getInt(rendererId, 0)
        if (height <= 0) {
            return null
        }
        return height
    }

    /** Whether the TV is expected to play [height]; unknown heights are. */
    fun fits(rendererId: String, height: Int?): Boolean {
        val refused = refusedHeight(rendererId)
        if (height == null || refused == null) {
            return true
        }
        return height < refused
    }

    fun recordRefused(rendererId: String, height: Int) {
        val refused = refusedHeight(rendererId)
        if (refused != null && refused <= height) {
            return
        }
        preferences.edit().putInt(rendererId, height).apply()
    }

    fun recordPlayed(rendererId: String, height: Int) {
        val refused = refusedHeight(rendererId) ?: return
        if (height >= refused) {
            preferences.edit().remove(rendererId).apply()
        }
    }
}
