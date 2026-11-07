package com.letsmoe.otter.ui

import com.letsmoe.otter.ui.navigation.Tab
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/** One rebuild of the app shell; it opens on startTab. */
data class ShellReload(val generation: Int, val startTab: Tab)

/**
 * Rebuilds the app shell, so every page (and its view model) loads its data again. For changes the
 * loaded pages can't follow on their own: a new TMDB language, TMDB reachable again. A rebuild
 * would stop playback, so requests wait while the player is open.
 */
object ShellReloads {

    private val mutableLatest = MutableStateFlow(ShellReload(generation = 0, startTab = Tab.HOME))
    val latest: StateFlow<ShellReload> = mutableLatest

    private val mutablePlayerOpen = MutableStateFlow(false)
    val playerOpen: StateFlow<Boolean> = mutablePlayerOpen

    private var pendingTab: Tab? = null

    fun request(startTab: Tab) {
        if (mutablePlayerOpen.value) {
            pendingTab = startTab
            return
        }
        rebuild(startTab)
    }

    fun setPlayerOpen(open: Boolean) {
        mutablePlayerOpen.value = open
        val waiting = pendingTab
        if (open || waiting == null) {
            return
        }
        pendingTab = null
        rebuild(waiting)
    }

    private fun rebuild(startTab: Tab) {
        mutableLatest.update { current -> ShellReload(current.generation + 1, startTab) }
    }
}
