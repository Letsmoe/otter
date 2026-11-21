package com.letsmoe.otter.cast.browse

import android.content.Context
import android.util.Log
import com.letsmoe.otter.AppScope
import com.letsmoe.otter.api.OtterApi
import com.letsmoe.otter.cast.DlnaPlayer
import com.letsmoe.otter.cast.TelevisionConnection
import com.letsmoe.otter.cast.TelevisionTarget
import com.letsmoe.otter.cast.session.SessionScreen
import com.letsmoe.otter.profiles.ProfileSession
import com.letsmoe.otter.settings.PlaybackSettings
import com.letsmoe.otter.ui.media.MediaRepository
import com.letsmoe.otter.ui.player.TelevisionPlayback
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

private const val TAG = "OtterScreensaver"
// Nothing playing and no title page open for this long: the TV is idle.
private const val IDLE_MILLISECONDS = 2 * 60 * 1000L

/**
 * Shows backdrops of the profile's titles on an idle DLNA TV, one after the other with a slow
 * crossfade and the title small in a corner. It is a screen of the TV's session stream (see
 * [DlnaPlayer.showIdleScreen]); the phone paints the changing picture into it
 * ([ScreensaverFrames]), so the TV loads nothing new. A title page, a playing title, the setting
 * turned off or a disconnected TV ends it.
 */
object TelevisionScreensaver {

    private lateinit var applicationContext: Context

    private val enabled = MutableStateFlow(true)
    private var running = false
    private var frameScope: CoroutineScope? = null

    fun initialize(context: Context) {
        applicationContext = context.applicationContext
        enabled.value = PlaybackSettings.televisionScreensaver
        val inputs = combine(
            TelevisionBrowse.browsing,
            TelevisionConnection.target,
            TelevisionPlayback.player,
            enabled,
        ) { browsing, target, player, isEnabled ->
            ScreensaverInputs(browsing != null, dlnaIdOf(target), player != null, isEnabled, showsScreens(target))
        }
        AppScope.launch {
            inputs.collectLatest { current -> follow(current) }
        }
    }

    /** The setting changed: switching it off takes the screensaver off the TV. */
    fun settingChanged() {
        enabled.value = PlaybackSettings.televisionScreensaver
    }

    private fun dlnaIdOf(target: TelevisionTarget?): String? {
        if (target is TelevisionTarget.Dlna) {
            return target.renderer.id
        }
        return null
    }

    private fun showsScreens(target: TelevisionTarget?): Boolean {
        if (target !is TelevisionTarget.Dlna) {
            return false
        }
        return target.player.showsScreens
    }

    private suspend fun follow(inputs: ScreensaverInputs) {
        when (nextScreensaverAction(inputs, running)) {
            ScreensaverAction.Keep -> Unit
            ScreensaverAction.Forget -> finish()
            ScreensaverAction.Clear -> clear()
            ScreensaverAction.StartWhenIdle -> {
                delay(IDLE_MILLISECONDS)
                start()
            }
        }
    }

    private fun finish() {
        running = false
        frameScope?.cancel()
        frameScope = null
    }

    // The session goes back to its idle screen.
    private fun clear() {
        finish()
        dlnaPlayer()?.showIdleScreen(SessionScreen.Idle)
    }

    private suspend fun start() {
        val player = dlnaPlayer() ?: return
        try {
            val slides = loadSlides()
            if (slides.isEmpty()) {
                return
            }
            val frames = startFrames(player, slides)
            // The title the browse picture showed is gone from the TV from here on.
            TelevisionBrowse.forgetShown()
            player.showIdleScreen(SessionScreen.Screensaver(frames))
            running = true
        } catch (cancellation: CancellationException) {
            finish()
            throw cancellation
        } catch (error: Exception) {
            finish()
            Log.w(TAG, "showing the screensaver on the TV failed", error)
        }
    }

    private fun dlnaPlayer(): DlnaPlayer? {
        val target = TelevisionConnection.target.value
        if (target is TelevisionTarget.Dlna) {
            return target.player
        }
        return null
    }

    // What the profile is watching, then My List, in a different order each time.
    private suspend fun loadSlides(): List<ScreensaverSlide> {
        OtterApi.awaitBackend()
        val profileId = ProfileSession.activeProfile.value?.id ?: return emptyList()
        val continueWatching = MediaRepository.continueWatching(profileId)
        val watchlist = MediaRepository.watchlist(profileId)
        return screensaverSlidesOf(continueWatching, watchlist).shuffled()
    }

    // Backdrops load in the background for as long as this screensaver runs.
    private fun startFrames(player: DlnaPlayer, slides: List<ScreensaverSlide>): ScreensaverFrames {
        finish()
        val height = player.screenHeight
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        frameScope = scope
        return ScreensaverFrames(slides, scope) { url -> loadBackdropBitmap(applicationContext, url, height) }
    }
}
