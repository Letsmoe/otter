package com.letsmoe.otter.cast

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

/** A TV Otter has cast to; [id] is the UDN, which stays the same when the TV gets another address. */
data class KnownTelevision(val id: String, val name: String, val lastCastAtMilliseconds: Long)

private const val FIELD_SEPARATOR = '\t'
private const val FIELD_COUNT = 3

/** "lastCastAt TAB id TAB name": the name last, so it may hold anything but a line break. */
fun encodeKnownTelevision(television: KnownTelevision): String {
    return "${television.lastCastAtMilliseconds}$FIELD_SEPARATOR${television.id}$FIELD_SEPARATOR${television.name}"
}

fun decodeKnownTelevision(entry: String): KnownTelevision? {
    val fields = entry.split(FIELD_SEPARATOR, limit = FIELD_COUNT)
    if (fields.size != FIELD_COUNT) {
        return null
    }
    val lastCastAt = fields[0].toLongOrNull() ?: return null
    return KnownTelevision(id = fields[1], name = fields[2], lastCastAtMilliseconds = lastCastAt)
}

/** The TV among the [found] ones that was cast to most recently; null when none was cast to before. */
fun suggestedTelevision(found: List<DlnaRenderer>, known: List<KnownTelevision>): DlnaRenderer? {
    val lastCastById = known.associate { television -> television.id to television.lastCastAtMilliseconds }
    val castBefore = found.filter { renderer -> renderer.id in lastCastById }
    return castBefore.maxByOrNull { renderer -> lastCastById.getValue(renderer.id) }
}

/** The DLNA TVs the user connected before, saved on this device. */
object KnownTelevisions {

    private const val PREFERENCES = "known_televisions"
    private const val KEY_ENTRIES = "entries"

    private lateinit var preferences: SharedPreferences

    fun initialize(context: Context) {
        preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
    }

    fun all(): List<KnownTelevision> {
        val entries = preferences.getStringSet(KEY_ENTRIES, emptySet()).orEmpty()
        return entries.mapNotNull(::decodeKnownTelevision)
    }

    /** Notes that [renderer] was cast to just now, under its current name. */
    fun remember(renderer: DlnaRenderer, nowMilliseconds: Long = System.currentTimeMillis()) {
        val others = all().filter { television -> television.id != renderer.id }
        val updated = others + KnownTelevision(renderer.id, renderer.name, nowMilliseconds)
        preferences.edit { putStringSet(KEY_ENTRIES, updated.map(::encodeKnownTelevision).toSet()) }
    }
}
