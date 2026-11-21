package com.letsmoe.otter.tv.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.letsmoe.otter.tv.theme.TvColors
import com.letsmoe.otter.tv.theme.TvSpacing
import com.letsmoe.otter.tv.theme.TvType
import com.letsmoe.otter.tv.ui.components.PillButton
import com.letsmoe.otter.ui.player.PlayerUiState
import com.letsmoe.otter.ui.player.SkipJump
import com.letsmoe.otter.ui.player.UpNextCard
import com.letsmoe.otter.ui.player.remainingMillisOf
import com.letsmoe.otter.vpn.VpnStatusStore
import kotlinx.coroutines.delay

/** Everything over the video: status, the controls, skip and up-next prompts, and the open panel. */
@Composable
fun PlayerOverlays(viewModel: TvPlayerViewModel, state: PlayerUiState, chrome: PlayerChrome) {
    val skipButton by viewModel.skipButton.collectAsStateWithLifecycle()
    val upNext by viewModel.upNext.collectAsStateWithLifecycle()
    val askingStillWatching by viewModel.askingStillWatching.collectAsStateWithLifecycle()
    // At the end of a title with nothing next, the controls stay up to play it again or leave.
    LaunchedEffect(state.hasEnded) {
        if (state.hasEnded) {
            chrome.showControls()
        }
    }
    Box(Modifier.fillMaxSize()) {
        StatusNotice(state, viewModel, chrome, Modifier.align(Alignment.Center))
        AnimatedVisibility(chrome.controlsVisible && chrome.panel == null, enter = fadeIn(), exit = fadeOut()) {
            ControlsOverlay(viewModel, state, chrome)
        }
        Prompts(viewModel, chrome, Prompt(skipButton, upNext, askingStillWatching))
        val panel = chrome.panel
        if (panel != null) {
            PlayerSidePanel(panel, state, viewModel, chrome, Modifier.align(Alignment.CenterEnd))
        }
    }
}

/** What the player asks or offers right now. */
private class Prompt(val skip: SkipJump?, val upNext: UpNextCard?, val askingStillWatching: Boolean)

// One prompt at a time, the most pressing first; none while a panel is open.
@Composable
private fun BoxScope.Prompts(viewModel: TvPlayerViewModel, chrome: PlayerChrome, prompt: Prompt) {
    if (chrome.panel != null) {
        return
    }
    if (prompt.askingStillWatching) {
        StillWatchingDialog(viewModel, Modifier.align(Alignment.Center))
        return
    }
    val upNext = prompt.upNext
    if (upNext != null) {
        UpNextPrompt(upNext, viewModel, Modifier.align(Alignment.BottomEnd))
        return
    }
    val skip = prompt.skip
    if (skip != null && !chrome.controlsVisible) {
        SkipPrompt(skip, viewModel, Modifier.align(Alignment.BottomEnd))
    }
}

/** Finding and loading links, buffering, and errors with a way out. */
@Composable
private fun StatusNotice(state: PlayerUiState, viewModel: TvPlayerViewModel, chrome: PlayerChrome, modifier: Modifier) {
    val error = state.shownError
    if (error != null) {
        ErrorNotice(error, viewModel, chrome, modifier)
        return
    }
    if (state.hasSource && !state.isBuffering) {
        return
    }
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        LoadingRing()
        if (!state.hasSource) {
            Text(sourceStatusText(state), fontSize = TvType.body, color = TvColors.secondaryContent)
        }
    }
}

// Without the required VPN the backend doesn't search at all.
@Composable
private fun sourceStatusText(state: PlayerUiState): String {
    val vpn by VpnStatusStore.status.collectAsStateWithLifecycle()
    if (vpn.blocksStreaming && vpn.bypassed) {
        return "Your VPN skips Otter: include it in the VPN app to watch"
    }
    if (vpn.blocksStreaming) {
        return "Connect to a VPN to watch"
    }
    return state.statusText
}

@Composable
private fun ErrorNotice(message: String, viewModel: TvPlayerViewModel, chrome: PlayerChrome, modifier: Modifier) {
    val retryButton = remember { FocusRequester() }
    LaunchedEffect(message) { retryButton.requestFocus() }
    Column(
        modifier.width(460.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(message, fontSize = TvType.body, textAlign = TextAlign.Center)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PillButton("Try again", onClick = viewModel::retryAfterError, primary = true, modifier = Modifier.focusRequester(retryButton))
            PillButton("Choose another source", onClick = { chrome.panel = PlayerPanel.SOURCES })
        }
    }
}

@Composable
private fun LoadingRing() {
    val spin = rememberInfiniteTransition(label = "spin")
    val angle by spin.animateFloat(0f, 360f, infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Restart), label = "angle")
    Canvas(Modifier.size(40.dp).graphicsLayer { rotationZ = angle }) {
        drawArc(Color.White.copy(alpha = 0.8f), startAngle = 0f, sweepAngle = 270f, useCenter = false, style = Stroke(width = 4.dp.toPx()))
    }
}

/** "Skip Intro" in the corner; it takes focus, so OK skips while the controls are hidden. */
@Composable
private fun SkipPrompt(skip: SkipJump, viewModel: TvPlayerViewModel, modifier: Modifier) {
    val skipFocus = remember { FocusRequester() }
    LaunchedEffect(skip) { skipFocus.requestFocus() }
    Box(modifier.padding(horizontal = TvSpacing.screenHorizontal, vertical = TvSpacing.screenVertical)) {
        PillButton(skip.label, onClick = viewModel::skipSegment, primary = true, modifier = Modifier.focusRequester(skipFocus))
    }
}

/** The next episode's still and a countdown during the credits; Play now has focus. */
@Composable
private fun UpNextPrompt(card: UpNextCard, viewModel: TvPlayerViewModel, modifier: Modifier) {
    val playFocus = remember { FocusRequester() }
    LaunchedEffect(card.episodeLabel) { playFocus.requestFocus() }
    val remainingSeconds = rememberCountdownSeconds(card)
    Column(
        modifier
            .padding(horizontal = TvSpacing.screenHorizontal, vertical = TvSpacing.screenVertical)
            .width(260.dp)
            .background(Color.Black.copy(alpha = 0.75f), RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AsyncImage(
            model = card.stillUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.aspectRatio(16f / 9f).clip(RoundedCornerShape(8.dp)),
        )
        Text("Next episode in $remainingSeconds s", fontSize = TvType.caption, color = TvColors.secondaryContent)
        Text(card.episodeLabel, fontSize = TvType.body, fontWeight = FontWeight.SemiBold, maxLines = 2)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PillButton("Play now", onClick = viewModel::playUpNextNow, primary = true, modifier = Modifier.focusRequester(playFocus))
            PillButton("Cancel", onClick = viewModel::cancelUpNext)
        }
    }
}

@Composable
private fun rememberCountdownSeconds(card: UpNextCard): Long {
    var remaining by remember(card) { mutableLongStateOf(remainingMillisOf(card)) }
    LaunchedEffect(card) {
        while (true) {
            remaining = remainingMillisOf(card)
            delay(250)
        }
    }
    return (remaining + 999) / 1000
}

@Composable
private fun StillWatchingDialog(viewModel: TvPlayerViewModel, modifier: Modifier) {
    val keepFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { keepFocus.requestFocus() }
    Column(
        modifier
            .background(Color.Black.copy(alpha = 0.85f), RoundedCornerShape(16.dp))
            .padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Are you still watching?", fontSize = TvType.screenTitle, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PillButton("Keep watching", onClick = viewModel::keepWatching, primary = true, modifier = Modifier.focusRequester(keepFocus))
            PillButton("Stop", onClick = viewModel::stopWatching)
        }
    }
}
