package dev.neoworks.otter.ui.player

import android.app.Application
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.Player
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Regular
import com.adamglin.phosphoricons.regular.Television
import dev.neoworks.otter.together.TogetherSession
import dev.neoworks.otter.together.isOpen
import dev.neoworks.otter.ui.components.WithoutBackdrop
import dev.neoworks.otter.ui.together.TogetherOverlay
import kotlinx.coroutines.delay

private const val CONTROLS_HIDE_MILLISECONDS = 3500L

/** Netflix-style fullscreen player for the phone, on Media3 with its own Compose controls. Mirrors Player.svelte. */
@Composable
fun PlayerScreen(request: PlayerRequest, onBack: () -> Unit) {
    val application = LocalContext.current.applicationContext as Application
    val viewModelKey = "${request.mediaId}|${request.streamId}|${request.downloadId}"
    val viewModel = viewModel(key = viewModelKey) { PlayerViewModel(application, request) }
    PlayerSystemEffects(onStop = viewModel::pauseForBackground)
    WithoutBackdrop {
        PlayerContent(viewModel, onBack)
    }
}

@Composable
private fun PlayerContent(viewModel: PlayerViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val position by viewModel.position.collectAsStateWithLifecycle()
    val skipButton by viewModel.skipButton.collectAsStateWithLifecycle()
    val upNext by viewModel.upNext.collectAsStateWithLifecycle()
    val room by TogetherSession.room.collectAsStateWithLifecycle()
    val chrome = remember { PlayerChrome() }
    var sheet by remember { mutableStateOf<PlayerSheetKind?>(null) }
    var skipFeedback by remember { mutableStateOf<SkipFeedback?>(null) }

    fun close() {
        viewModel.close()
        onBack()
    }

    fun openSheet(kind: PlayerSheetKind) {
        sheet = kind
        chrome.visible = true
    }

    fun closeSheet() {
        sheet = null
        chrome.show()
    }

    HideControlsAfterIdle(state, chrome, sheet)
    BackHandler { if (sheet != null) closeSheet() else close() }

    val actions = PlayerActions(
        close = ::close,
        togglePlay = { viewModel.togglePlay(); chrome.show() },
        skip = { seconds -> viewModel.skip(seconds); chrome.show() },
        seek = { seconds -> viewModel.seekTo(seconds); chrome.show() },
        preview = { seconds -> onPreview(viewModel, chrome, seconds) },
        openSheet = ::openSheet,
        playNext = { viewModel.playNext(); chrome.show() },
        toggleLock = { chrome.locked = !chrome.locked; sheet = null; chrome.show() },
    )

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        VideoSurface(viewModel.player)
        state.castDeviceName?.let { deviceName -> CastingBackdrop(deviceName) }
        PlayerTapSurface(
            onToggleControls = chrome::toggle,
            onDoubleTap = { side ->
                if (chrome.locked) {
                    chrome.toggle()
                } else {
                    skipFeedback = SkipFeedback(side, System.currentTimeMillis())
                    viewModel.skip(skipAmount(side))
                }
            },
        )
        skipFeedback?.let { feedback -> SkipFeedbackBubble(feedback) }
        StatusNotice(state, viewModel, ::openSheet, ::close)
        if (!state.hasEnded) {
            PlayerControlsLayer(state, position, chrome, actions) { viewModel.previewPlayer }
            SkipSegmentButton(skipButton.takeIf { upNext == null }, chrome.visible, viewModel::skipSegment)
            // Out of the controls' way; the countdown keeps running and the card returns with it.
            UpNextOverlay(upNext.takeIf { !chrome.visible }, chrome.visible, viewModel::cancelUpNext, viewModel::playUpNextNow)
        }
        if (room.isOpen()) {
            TogetherOverlay()
        }
        AnimatedVisibility(state.hasEnded, enter = fadeIn(), exit = fadeOut()) {
            EndScreen(state.title, state.mediaId, state.hasNextEpisode, onNext = viewModel::playNextAfterEnd, onClose = ::close)
        }
        PlayerSheetHost(sheet, onClose = ::closeSheet) { kind -> SheetContent(kind, state, viewModel, ::closeSheet) }
    }
}

private fun skipAmount(side: TapSide): Int {
    if (side == TapSide.LEFT) {
        return -SKIP_SECONDS
    }
    return SKIP_SECONDS
}

private fun onPreview(viewModel: PlayerViewModel, chrome: PlayerChrome, seconds: Double?) {
    chrome.previewSeconds = seconds
    chrome.visible = true
    if (seconds != null) {
        viewModel.previewAt(seconds)
    }
}

// The controls hide a moment after playback starts; a pause, a sheet or scrubbing keeps them up.
@Composable
private fun HideControlsAfterIdle(state: PlayerUiState, chrome: PlayerChrome, sheet: PlayerSheetKind?) {
    LaunchedEffect(state.isPlaying) {
        if (!state.isPlaying) {
            chrome.show()
        }
    }
    LaunchedEffect(chrome.visible, chrome.interactions, state.isPlaying, sheet, chrome.previewSeconds) {
        val canHide = chrome.visible && state.isPlaying && sheet == null && chrome.previewSeconds == null
        if (canHide) {
            delay(CONTROLS_HIDE_MILLISECONDS)
            chrome.visible = false
        }
    }
}

@Composable
private fun VideoSurface(player: Player) {
    AndroidView(
        factory = { context ->
            PlayerView(context).apply {
                useController = false
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                setShutterBackgroundColor(android.graphics.Color.BLACK)
                this.player = player
            }
        },
        modifier = Modifier.fillMaxSize(),
    )
}

// The video runs on the TV; the phone shows where and keeps the controls.
@Composable
private fun CastingBackdrop(deviceName: String) {
    Column(
        Modifier.fillMaxSize().background(Color.Black),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(PhosphorIcons.Regular.Television, contentDescription = null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(56.dp))
        Text("Playing on $deviceName", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color.White.copy(alpha = 0.85f))
    }
}

// Waiting for the room, buffering, or a failure, in the middle of the video.
@Composable
private fun StatusNotice(state: PlayerUiState, viewModel: PlayerViewModel, openSheet: (PlayerSheetKind) -> Unit, close: () -> Unit) {
    Box(Modifier.fillMaxSize()) {
        val error = state.shownError
        if (error != null) {
            PlayerErrorNotice(error, onChooseAnotherSource = { chooseAnotherSource(state, openSheet, close) }, modifier = Modifier.align(Alignment.Center))
            return@Box
        }
        if (state.hasEnded) {
            return@Box
        }
        if (state.isHolding && state.wantPlaying && state.hasSource) {
            HoldingNotice(state, onStartWithoutWaiting = viewModel::startWithoutWaiting, modifier = Modifier.align(Alignment.Center))
            return@Box
        }
        if (state.isBuffering) {
            BufferingIndicator(state, Modifier.align(Alignment.Center))
        }
    }
}

private fun chooseAnotherSource(state: PlayerUiState, openSheet: (PlayerSheetKind) -> Unit, close: () -> Unit) {
    if (state.sources.size > 1) {
        openSheet(PlayerSheetKind.SOURCES)
        return
    }
    close()
}

@Composable
private fun SheetContent(kind: PlayerSheetKind, state: PlayerUiState, viewModel: PlayerViewModel, closeSheet: () -> Unit) {
    when (kind) {
        PlayerSheetKind.SPEED -> SpeedSheetContent(state.speed, viewModel::chooseSpeed)
        PlayerSheetKind.SOURCES -> SourcesSheetContent(state.sources, state.currentSourceId) { sourceId ->
            closeSheet()
            viewModel.chooseSource(sourceId)
        }
        PlayerSheetKind.TRACKS -> TracksSheetContent(state, viewModel::chooseAudio, viewModel::chooseSubtitle)
        PlayerSheetKind.EPISODES -> EpisodesSheetContent(state.episodes, state.mediaId) { episodeId ->
            closeSheet()
            viewModel.openEpisode(episodeId)
        }
        PlayerSheetKind.TOGETHER -> PlayerTogetherPane()
        PlayerSheetKind.TELEVISION -> TelevisionSheetContent(
            state,
            onChoose = { renderer ->
                closeSheet()
                viewModel.playOnTelevision(renderer)
            },
            onStop = {
                closeSheet()
                viewModel.stopTelevision()
            },
        )
    }
}
