package com.letsmoe.otter.ui.media

import com.letsmoe.otter.AppScope
import com.letsmoe.otter.toasts.ToastAction
import com.letsmoe.otter.toasts.ToastKind
import com.letsmoe.otter.toasts.Toasts
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

/**
 * Titles a profile hid with "Not for me" in this session. The backend keeps them out of later
 * recommendations; this set bridges the time until a page reloads its rows.
 */
object RecommendationDismissals {

    private val mutableHidden = MutableStateFlow<Map<String, Set<String>>>(emptyMap())

    /** Hidden title ids by profile id. */
    val hidden: StateFlow<Map<String, Set<String>>> = mutableHidden

    /** Hides the title for the profile and offers an undo; returns false when the backend refused. */
    suspend fun dismiss(profileId: String, titleId: String, titleName: String): Boolean {
        try {
            TitleActions.dismissRecommendation(profileId, titleId)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            Toasts.push("Could not hide $titleName", ToastKind.ERROR)
            return false
        }
        hide(profileId, titleId)
        val undo = ToastAction("Undo") { AppScope.launch { restore(profileId, titleId, titleName) } }
        Toasts.push("$titleName won't be recommended again", ToastKind.INFO, undo)
        return true
    }

    private suspend fun restore(profileId: String, titleId: String, titleName: String) {
        try {
            TitleActions.restoreRecommendation(profileId, titleId)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            Toasts.push("Could not bring back $titleName", ToastKind.ERROR)
            return
        }
        mutableHidden.update { current -> current.withoutTitle(profileId, titleId) }
    }

    private fun hide(profileId: String, titleId: String) {
        mutableHidden.update { current -> current.withTitle(profileId, titleId) }
    }
}

internal fun Map<String, Set<String>>.withTitle(profileId: String, titleId: String): Map<String, Set<String>> {
    val ofProfile = getOrDefault(profileId, emptySet())
    return this + (profileId to (ofProfile + titleId))
}

internal fun Map<String, Set<String>>.withoutTitle(profileId: String, titleId: String): Map<String, Set<String>> {
    val ofProfile = getOrDefault(profileId, emptySet())
    return this + (profileId to (ofProfile - titleId))
}

/** Drops the titles the profile hid, so a row never shows one again. */
fun List<RailItem>.withoutHidden(hiddenIds: Set<String>): List<RailItem> {
    if (hiddenIds.isEmpty()) {
        return this
    }
    return filter { item -> item.id !in hiddenIds }
}
