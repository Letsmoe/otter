package com.letsmoe.otter.tv.ui.player

import android.app.Application
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import com.letsmoe.otter.ui.player.CaptionLook
import com.letsmoe.otter.ui.player.phoneCaptionStyle
import kotlinx.coroutines.delay

// The controls hide after this long without a key while the video plays.
private const val CONTROLS_HIDE_MILLISECONDS = 5_000L
private const val SEEK_STEP_SECONDS = 10

/** The panels that slide in from the right over the video. */
enum class PlayerPanel { EPISODES, AUDIO, SOURCES }

/**
 * What the screen shows besides the video: the controls (any key brings them up) and one panel.
 * [lastKeyMillis] restarts the hide countdown.
 */
class PlayerChrome {
    var controlsVisible by mutableStateOf(true)
    var panel by mutableStateOf<PlayerPanel?>(null)
    var lastKeyMillis by mutableLongStateOf(System.currentTimeMillis())

    fun showControls() {
        controlsVisible = true
        lastKeyMillis = System.currentTimeMillis()
    }
}

/** Plays [mediaId] full screen; Back leaves once the controls and panels are closed. */
@Composable
fun TvPlayerScreen(mediaId: String, streamId: String?, onBack: () -> Unit) {
    val application = LocalContext.current.applicationContext as Application
    val viewModel = viewModel(key = "tv-player-$mediaId-$streamId") { TvPlayerViewModel(application, mediaId, streamId) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val chrome = remember { PlayerChrome() }
    val videoFocus = remember { FocusRequester() }
    HideControlsWhilePlaying(chrome, state.isPlaying)
    BackHandler { onPlayerBack(chrome, onBack) }
    LaunchedEffect(chrome.controlsVisible, chrome.panel) {
        if (!chrome.controlsVisible && chrome.panel == null) {
            videoFocus.requestFocus()
        }
    }
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onPreviewKeyEvent { event -> noteRemoteUse(event, viewModel, chrome) },
    ) {
        PlayerVideo(viewModel, state.captionLook)
        // Takes the remote's keys while no control is on screen.
        Box(
            Modifier
                .fillMaxSize()
                .focusRequester(videoFocus)
                .focusable()
                .onKeyEvent { event -> handleVideoKey(event, viewModel, chrome) },
        )
        PlayerOverlays(viewModel, state, chrome)
    }
}

// Back closes the panel, then the controls, then the player.
private fun onPlayerBack(chrome: PlayerChrome, onBack: () -> Unit) {
    if (chrome.panel != null) {
        chrome.panel = null
        chrome.showControls()
        return
    }
    if (chrome.controlsVisible) {
        chrome.controlsVisible = false
        return
    }
    onBack()
}

@Composable
private fun HideControlsWhilePlaying(chrome: PlayerChrome, playing: Boolean) {
    LaunchedEffect(chrome.lastKeyMillis, chrome.controlsVisible, chrome.panel, playing) {
        if (!chrome.controlsVisible || chrome.panel != null || !playing) {
            return@LaunchedEffect
        }
        delay(CONTROLS_HIDE_MILLISECONDS)
        chrome.controlsVisible = false
    }
}

// Every key counts: the controls stay up, and the next episode needn't ask "Still watching?".
private fun noteRemoteUse(event: KeyEvent, viewModel: TvPlayerViewModel, chrome: PlayerChrome): Boolean {
    if (event.type == KeyEventType.KeyDown) {
        viewModel.remoteUsed()
        chrome.lastKeyMillis = System.currentTimeMillis()
    }
    return handleMediaKey(event, viewModel, chrome)
}

/** The remote's media keys work everywhere on the screen. */
private fun handleMediaKey(event: KeyEvent, viewModel: TvPlayerViewModel, chrome: PlayerChrome): Boolean {
    if (event.type != KeyEventType.KeyDown) {
        return false
    }
    when (event.key) {
        Key.MediaPlayPause, Key.MediaPlay, Key.MediaPause -> viewModel.togglePlay()
        Key.MediaFastForward -> viewModel.skip(SEEK_STEP_SECONDS)
        Key.MediaRewind -> viewModel.skip(-SEEK_STEP_SECONDS)
        else -> return false
    }
    chrome.showControls()
    return true
}

/** With the controls hidden: OK pauses or plays, left and right jump 10 s, anything else shows the controls. */
private fun handleVideoKey(event: KeyEvent, viewModel: TvPlayerViewModel, chrome: PlayerChrome): Boolean {
    if (event.type != KeyEventType.KeyDown) {
        return false
    }
    when (event.key) {
        Key.DirectionCenter, Key.Enter -> viewModel.togglePlay()
        Key.DirectionLeft -> viewModel.skip(-SEEK_STEP_SECONDS)
        Key.DirectionRight -> viewModel.skip(SEEK_STEP_SECONDS)
        Key.DirectionUp, Key.DirectionDown -> Unit
        else -> return false
    }
    chrome.showControls()
    return true
}

/** The video with captions in the profile's size and style; never takes focus itself. */
@OptIn(UnstableApi::class)
@Composable
private fun PlayerVideo(viewModel: TvPlayerViewModel, captionLook: CaptionLook) {
    AndroidView(
        factory = { context ->
            PlayerView(context).apply {
                useController = false
                isFocusable = false
                keepScreenOn = true
                player = viewModel.player
                subtitleView?.setApplyEmbeddedStyles(false)
                subtitleView?.setApplyEmbeddedFontSizes(false)
            }
        },
        update = { view ->
            view.subtitleView?.setStyle(phoneCaptionStyle(captionLook.style))
            view.subtitleView?.setFractionalTextSize(captionLook.textFraction)
        },
        modifier = Modifier.fillMaxSize(),
    )
}
