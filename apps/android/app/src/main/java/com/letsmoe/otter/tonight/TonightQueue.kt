package com.letsmoe.otter.tonight

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

// A queue is for one evening: a day later it is gone.
private const val QUEUE_LIFETIME_MILLISECONDS = 18 * 60 * 60 * 1000L

/** A title lined up for tonight; [label] names it on the cards, e.g. "Severance · S1:E3 Half Loop". */
data class TonightEntry(val mediaId: String, val label: String)

/** Where a title stands in the queue: "2 of 3 tonight". */
data class TonightPosition(val number: Int, val total: Int) {
    val label: String
        get() = "$number of $total tonight"
}

/** The queue as it was kept, and when it last changed. */
data class StoredTonight(val entries: List<TonightEntry>, val savedAtMillis: Long)

interface TonightStorage {
    fun read(): StoredTonight?

    fun write(stored: StoredTonight)
}

/** Saved on this device only, one line per title. */
class PreferencesTonightStorage(context: Context) : TonightStorage {

    private val preferences = context.getSharedPreferences("tonight", Context.MODE_PRIVATE)

    override fun read(): StoredTonight? {
        val savedAtMillis = preferences.getLong("savedAt", -1)
        val lines = preferences.getString("entries", null)
        if (savedAtMillis < 0 || lines == null) {
            return null
        }
        return StoredTonight(lines.lines().mapNotNull(::entryOfLine), savedAtMillis)
    }

    override fun write(stored: StoredTonight) {
        preferences.edit {
            putLong("savedAt", stored.savedAtMillis)
            putString("entries", stored.entries.joinToString("\n", transform = ::lineOfEntry))
        }
    }
}

// Tabs and line breaks separate the fields and entries, so they can't be in a label.
fun lineOfEntry(entry: TonightEntry): String {
    val label = entry.label.replace('\t', ' ').replace('\n', ' ')
    return "${entry.mediaId}\t$label"
}

fun entryOfLine(line: String): TonightEntry? {
    val parts = line.split("\t", limit = 2)
    if (parts.size != 2 || parts[0].isEmpty()) {
        return null
    }
    return TonightEntry(parts[0], parts[1])
}

/**
 * The titles lined up for tonight, in the order they play: when one ends, the player starts the
 * next one in the queue instead of the next episode.
 */
class TonightQueue(private val storage: TonightStorage, private val clockMillis: () -> Long = System::currentTimeMillis) {

    private val mutableEntries = MutableStateFlow(loadFresh())
    val entries: StateFlow<List<TonightEntry>> = mutableEntries

    private fun loadFresh(): List<TonightEntry> {
        val stored = storage.read() ?: return emptyList()
        if (clockMillis() - stored.savedAtMillis > QUEUE_LIFETIME_MILLISECONDS) {
            return emptyList()
        }
        return stored.entries
    }

    fun contains(mediaId: String): Boolean {
        return mutableEntries.value.any { entry -> entry.mediaId == mediaId }
    }

    /** Appends the title; one already queued stays where it is. */
    fun add(entry: TonightEntry) {
        if (contains(entry.mediaId)) {
            return
        }
        change(mutableEntries.value + entry)
    }

    fun remove(mediaId: String) {
        change(mutableEntries.value.filter { entry -> entry.mediaId != mediaId })
    }

    fun clear() {
        change(emptyList())
    }

    /** The title queued right after [mediaId]; null when it isn't queued or is the last one. */
    fun nextAfter(mediaId: String): TonightEntry? {
        val queued = mutableEntries.value
        val index = queued.indexOfFirst { entry -> entry.mediaId == mediaId }
        return queued.getOrNull(index + 1).takeIf { index >= 0 }
    }

    /** Where [mediaId] stands; null when it isn't queued or is alone in the queue. */
    fun positionOf(mediaId: String): TonightPosition? {
        val queued = mutableEntries.value
        val index = queued.indexOfFirst { entry -> entry.mediaId == mediaId }
        if (index < 0 || queued.size < 2) {
            return null
        }
        return TonightPosition(index + 1, queued.size)
    }

    private fun change(entries: List<TonightEntry>) {
        mutableEntries.value = entries
        storage.write(StoredTonight(entries, clockMillis()))
    }

    companion object {
        private var instance: TonightQueue? = null

        /** The app's queue; [initialize] comes first. */
        val shared: TonightQueue
            get() = checkNotNull(instance) { "TonightQueue.initialize comes first" }

        fun initialize(context: Context) {
            instance = TonightQueue(PreferencesTonightStorage(context))
        }
    }
}
