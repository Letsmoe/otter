package com.letsmoe.otter.cast

import android.content.Context
import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

// A search takes seconds on the Wi-Fi; title pages opened within this time reuse its answer.
private const val SEARCH_VALID_MILLISECONDS = 2 * 60_000L

// A title started from the widget waits this long for the search before it plays on the phone.
private const val SEARCH_WAIT_MILLISECONDS = 4_000L

/**
 * The TV Otter has cast to before that answers on the Wi-Fi now, for the title page's
 * "Play on <TV>". Searches happen in the background and are not repeated for two minutes.
 */
object SuggestedTelevision {

    private val mutableTelevision = MutableStateFlow<DlnaRenderer?>(null)
    val television: StateFlow<DlnaRenderer?> = mutableTelevision

    private val searchLock = Mutex()
    private var lastSearchAtMilliseconds: Long? = null

    suspend fun refresh(context: Context) {
        val known = KnownTelevisions.all()
        if (known.isEmpty() || searchedRecently()) {
            return
        }
        searchLock.withLock {
            if (searchedRecently()) {
                return
            }
            val found = discoverRenderers(context)
            lastSearchAtMilliseconds = SystemClock.elapsedRealtime()
            mutableTelevision.value = suggestedTelevision(found, known)
        }
    }

    /**
     * Connects the suggested TV when none is connected, so a title started from outside the app (the
     * home screen widget) plays on it. The search waits a few seconds at most; without an answer
     * the title plays on the phone.
     */
    suspend fun connectIfIdle(context: Context) {
        if (TelevisionConnection.isConnected) {
            return
        }
        withTimeoutOrNull(SEARCH_WAIT_MILLISECONDS) { refresh(context) }
        val suggested = mutableTelevision.value ?: return
        TelevisionConnection.connectDlna(suggested)
    }

    private fun searchedRecently(): Boolean {
        val lastSearchAt = lastSearchAtMilliseconds ?: return false
        return SystemClock.elapsedRealtime() - lastSearchAt < SEARCH_VALID_MILLISECONDS
    }
}
