package com.letsmoe.otter.cast.browse

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.letsmoe.otter.AppScope
import com.letsmoe.otter.cast.DlnaPlayer
import com.letsmoe.otter.cast.TelevisionConnection
import com.letsmoe.otter.cast.TelevisionTarget
import com.letsmoe.otter.cast.session.SessionScreen
import com.letsmoe.otter.settings.PlaybackSettings
import com.letsmoe.otter.ui.player.TelevisionPlayback
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private const val TAG = "OtterBrowse"
// The title page changes often while the user flicks through titles; drawing each one is wasted work.
private const val SETTLE_MILLISECONDS = 1200L

/**
 * Shows the title the phone is looking at on the connected DLNA TV while
 * nothing plays: the title page asks with [show], the phone draws the picture
 * ([drawBrowseFrame]) and the TV's session stream shows it (see
 * [DlnaPlayer.showIdleScreen]), so the TV loads nothing new. The newest
 * request wins; a title that starts playing or a TV that goes away ends it.
 */
object TelevisionBrowse {

    private lateinit var applicationContext: Context

    private val requested = MutableStateFlow<BrowsePreview?>(null)

    /** The title whose page is open; null when none is (the screensaver waits for that). */
    val browsing: StateFlow<BrowsePreview?> = requested
    private val enabled = MutableStateFlow(true)
    private var shown: ShownPreview? = null
    // The picture the session shows now; deleted once another one replaces it.
    private var shownPicture: File? = null

    fun initialize(context: Context) {
        applicationContext = context.applicationContext
        enabled.value = PlaybackSettings.televisionBrowse
        val inputs = combine(
            requested,
            TelevisionConnection.target,
            TelevisionPlayback.player,
            enabled,
        ) { preview, target, player, isEnabled ->
            BrowseInputs(preview, dlnaIdOf(target), player != null, isEnabled, showsScreens(target))
        }
        AppScope.launch {
            inputs.collectLatest { current -> follow(current) }
        }
    }

    /** The title page [preview] belongs to is open; the TV shows it soon. */
    fun show(preview: BrowsePreview) {
        requested.value = preview
    }

    /** The page of [titleId] closed; what the TV shows stays until another title or a playback replaces it. */
    fun leave(titleId: String) {
        requested.update { current -> stillRequested(current, titleId) }
    }

    private fun stillRequested(current: BrowsePreview?, closedTitleId: String): BrowsePreview? {
        if (current?.titleId == closedTitleId) {
            return null
        }
        return current
    }

    /** Something else took over the TV's picture (the screensaver): the title shown is shown no more. */
    fun forgetShown() {
        shown = null
    }

    /** The setting changed: switching it off takes the picture off the TV. */
    fun settingChanged() {
        enabled.value = PlaybackSettings.televisionBrowse
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

    private suspend fun follow(inputs: BrowseInputs) {
        when (val action = nextBrowseAction(inputs, shown)) {
            is BrowseAction.Keep -> Unit
            is BrowseAction.Forget -> shown = null
            is BrowseAction.Clear -> clear()
            is BrowseAction.Show -> showOnTelevision(action.preview)
        }
    }

    // The session goes back to its idle screen.
    private fun clear() {
        val player = dlnaPlayer() ?: return
        shown = null
        player.showIdleScreen(SessionScreen.Idle)
    }

    private suspend fun showOnTelevision(preview: BrowsePreview) {
        val player = dlnaPlayer() ?: return
        delay(SETTLE_MILLISECONDS)
        // Unknown from here on: a cancelled drawing can leave the TV on either title.
        shown = null
        try {
            val picture = drawPicture(player, preview)
            player.showIdleScreen(SessionScreen.Picture(picture))
            replaceShownPicture(picture)
            shown = ShownPreview(player.renderer.id, preview)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            Log.w(TAG, "showing ${preview.title} on the TV failed", error)
        }
    }

    private fun dlnaPlayer(): DlnaPlayer? {
        val target = TelevisionConnection.target.value
        if (target is TelevisionTarget.Dlna) {
            return target.player
        }
        return null
    }

    // Draws the picture at the session's size and saves it for the encoder to read.
    private suspend fun drawPicture(player: DlnaPlayer, preview: BrowsePreview): File {
        val height = player.screenHeight
        var backdrop: Bitmap? = null
        if (preview.backdropUrl != null) {
            backdrop = loadBackdropBitmap(applicationContext, preview.backdropUrl, height)
        }
        val picture = withContext(Dispatchers.Default) { renderBrowseFrame(preview, backdrop, height) }
        return writeStillPicture(applicationContext, picture, "browse")
    }

    private fun replaceShownPicture(picture: File) {
        shownPicture?.delete()
        shownPicture = picture
    }
}
