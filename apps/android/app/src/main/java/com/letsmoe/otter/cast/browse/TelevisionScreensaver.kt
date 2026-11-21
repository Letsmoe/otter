package com.letsmoe.otter.cast.browse

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.util.Log
import com.letsmoe.otter.AppScope
import com.letsmoe.otter.api.OtterApi
import com.letsmoe.otter.cast.DlnaPlayer
import com.letsmoe.otter.cast.TelevisionConnection
import com.letsmoe.otter.cast.TelevisionTarget
import com.letsmoe.otter.cast.overlay.StillJob
import com.letsmoe.otter.cast.overlay.StillJobs
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
// A TV that watched all of it goes back to its own screen; this covers a night.
private const val SCREENSAVER_SECONDS = 6 * 60 * 60

/**
 * Shows backdrops of the profile's titles on an idle DLNA TV, one after the other with a slow
 * crossfade and the title small in a corner. The TV loads one long stream once; the phone paints
 * the changing picture into it ([ScreensaverFrames]), so no new load is needed per image. A title
 * page, a playing title, the setting turned off or a disconnected TV ends it.
 */
object TelevisionScreensaver {

    private lateinit var applicationContext: Context

    private val enabled = MutableStateFlow(true)
    private var running = false
    private var frameScope: CoroutineScope? = null

    @Volatile
    private var encoderFailed = false

    fun initialize(context: Context) {
        applicationContext = context.applicationContext
        enabled.value = PlaybackSettings.televisionScreensaver
        val inputs = combine(
            TelevisionBrowse.browsing,
            TelevisionConnection.target,
            TelevisionPlayback.player,
            enabled,
        ) { browsing, target, player, isEnabled ->
            ScreensaverInputs(browsing != null, dlnaIdOf(target), player != null, isEnabled, encoderUsable(target))
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

    private fun encoderUsable(target: TelevisionTarget?): Boolean {
        if (target !is TelevisionTarget.Dlna) {
            return false
        }
        return !encoderFailed && target.player.encodesPictures
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

    private suspend fun clear() {
        val player = dlnaPlayer()
        finish()
        if (player == null) {
            return
        }
        try {
            player.clearStill()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            Log.w(TAG, "taking the screensaver off the TV failed", error)
        }
    }

    private suspend fun start() {
        val player = dlnaPlayer() ?: return
        val slides = loadSlides()
        if (slides.isEmpty()) {
            return
        }
        try {
            val streamPath = prepareStream(player, slides)
            // The title the browse picture showed is gone from the TV from here on.
            TelevisionBrowse.forgetShown()
            player.showStill(streamPath, "Otter")
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

    // The picture the TV starts from is plain black; the live frames cover all of it.
    private suspend fun prepareStream(player: DlnaPlayer, slides: List<ScreensaverSlide>): String {
        val height = player.stillHeight
        val black = Bitmap.createBitmap(height * 16 / 9, height, Bitmap.Config.ARGB_8888)
        black.eraseColor(Color.BLACK)
        val file = writeStillPicture(applicationContext, black, "screensaver")
        finish()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        frameScope = scope
        val frames = ScreensaverFrames(slides, scope) { url -> loadBackdropBitmap(applicationContext, url, height) }
        val job = StillJob(file, SCREENSAVER_SECONDS, frames) { error -> onEncoderFailed(error) }
        StillJobs.register(job)
        return "/api/stream/overlay?job=${job.id}"
    }

    // The TV gets no more pictures from the phone until the app restarts.
    private fun onEncoderFailed(error: Exception) {
        Log.w(TAG, "encoding the screensaver failed", error)
        encoderFailed = true
    }
}
