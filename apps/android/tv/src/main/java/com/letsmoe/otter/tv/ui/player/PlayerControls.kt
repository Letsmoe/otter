package com.letsmoe.otter.tv.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Bold
import com.adamglin.phosphoricons.Fill
import com.adamglin.phosphoricons.bold.ClockClockwise
import com.adamglin.phosphoricons.bold.ClockCounterClockwise
import com.adamglin.phosphoricons.bold.ClosedCaptioning
import com.adamglin.phosphoricons.bold.Link
import com.adamglin.phosphoricons.bold.ListBullets
import com.adamglin.phosphoricons.bold.SkipForward
import com.adamglin.phosphoricons.fill.Pause
import com.adamglin.phosphoricons.fill.Play
import com.letsmoe.otter.tv.theme.TvColors
import com.letsmoe.otter.tv.theme.TvSpacing
import com.letsmoe.otter.tv.theme.TvType
import com.letsmoe.otter.tv.ui.components.FocusableSurface
import com.letsmoe.otter.tv.ui.components.PillButton
import com.letsmoe.otter.ui.player.PlaybackPosition
import com.letsmoe.otter.ui.player.PlayerUiState
import com.letsmoe.otter.ui.player.formatPlayerTime

private const val SCRUB_STEP_SECONDS = 10

/** The title on top, the progress bar and the buttons at the bottom, over darkened edges of the video. */
@Composable
fun ControlsOverlay(viewModel: TvPlayerViewModel, state: PlayerUiState, chrome: PlayerChrome) {
    val position by viewModel.position.collectAsStateWithLifecycle()
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color.Black.copy(alpha = 0.7f), 0.25f to Color.Transparent, 0.6f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.85f))))
        Text(
            state.title,
            fontSize = TvType.screenTitle,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = TvSpacing.screenHorizontal, vertical = TvSpacing.screenVertical),
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(horizontal = TvSpacing.screenHorizontal, vertical = TvSpacing.screenVertical),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Scrubber(position, state, viewModel)
            ControlButtons(state, viewModel, chrome)
        }
    }
}

/** The progress bar: left and right on it jump 10 s, OK pauses or plays; times on both sides. */
@Composable
private fun Scrubber(position: PlaybackPosition, state: PlayerUiState, viewModel: TvPlayerViewModel) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(formatPlayerTime(position.positionSeconds), fontSize = TvType.detail, color = TvColors.secondaryContent)
        Box(
            Modifier
                .weight(1f)
                .height(20.dp)
                .focusable(interactionSource = interactionSource)
                .onKeyEvent { event -> scrubberKey(event, viewModel) },
            contentAlignment = Alignment.CenterStart,
        ) {
            ScrubberTrack(position, state.durationSeconds, focused)
        }
        Text(formatPlayerTime(state.durationSeconds), fontSize = TvType.detail, color = TvColors.secondaryContent)
    }
}

private fun scrubberKey(event: KeyEvent, viewModel: TvPlayerViewModel): Boolean {
    if (event.type != KeyEventType.KeyDown) {
        return false
    }
    when (event.key) {
        Key.DirectionLeft -> viewModel.skip(-SCRUB_STEP_SECONDS)
        Key.DirectionRight -> viewModel.skip(SCRUB_STEP_SECONDS)
        Key.DirectionCenter, Key.Enter -> viewModel.togglePlay()
        else -> return false
    }
    return true
}

/** Played, buffered and the rest; focused, the bar thickens and shows a knob at the playing position. */
@Composable
private fun ScrubberTrack(position: PlaybackPosition, durationSeconds: Double, focused: Boolean) {
    val played = fractionOf(position.positionSeconds, durationSeconds)
    val buffered = fractionOf(position.bufferedSeconds, durationSeconds)
    var barHeight = 4.dp
    if (focused) {
        barHeight = 6.dp
    }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().height(barHeight).background(Color.White.copy(alpha = 0.25f), CircleShape))
        Box(Modifier.fillMaxWidth(buffered).height(barHeight).background(Color.White.copy(alpha = 0.4f), CircleShape))
        Box(Modifier.fillMaxWidth(played).height(barHeight).background(TvColors.accent, CircleShape))
        if (focused) {
            Box(
                Modifier
                    .offset(x = maxWidth * played - 8.dp, y = barHeight / 2 - 8.dp)
                    .size(16.dp)
                    .background(Color.White, CircleShape)
                    .border(2.dp, TvColors.accent, CircleShape),
            )
        }
    }
}

private fun fractionOf(seconds: Double, durationSeconds: Double): Float {
    if (durationSeconds <= 0) {
        return 0f
    }
    return (seconds / durationSeconds).toFloat().coerceIn(0f, 1f)
}

/** Play and the 10 s jumps on the left; episodes, audio and captions, sources and the next episode on the right. */
@Composable
private fun ControlButtons(state: PlayerUiState, viewModel: TvPlayerViewModel, chrome: PlayerChrome) {
    val playButton = remember { FocusRequester() }
    LaunchedEffect(Unit) { playButton.requestFocus() }
    var playIcon = PhosphorIcons.Fill.Play
    if (state.isPlaying) {
        playIcon = PhosphorIcons.Fill.Pause
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        RoundButton(playIcon, "Play or pause", onClick = viewModel::togglePlay, modifier = Modifier.focusRequester(playButton))
        RoundButton(PhosphorIcons.Bold.ClockCounterClockwise, "Back 10 seconds", onClick = { viewModel.skip(-SCRUB_STEP_SECONDS) })
        RoundButton(PhosphorIcons.Bold.ClockClockwise, "Forward 10 seconds", onClick = { viewModel.skip(SCRUB_STEP_SECONDS) })
        Spacer(Modifier.weight(1f))
        PanelButtons(state, viewModel, chrome)
    }
}

@Composable
private fun PanelButtons(state: PlayerUiState, viewModel: TvPlayerViewModel, chrome: PlayerChrome) {
    if (state.episodes.isNotEmpty()) {
        PillButton("Episodes", onClick = { chrome.panel = PlayerPanel.EPISODES }, icon = PhosphorIcons.Bold.ListBullets)
    }
    PillButton("Audio & subtitles", onClick = { chrome.panel = PlayerPanel.AUDIO }, icon = PhosphorIcons.Bold.ClosedCaptioning)
    PillButton("Sources", onClick = { chrome.panel = PlayerPanel.SOURCES }, icon = PhosphorIcons.Bold.Link)
    if (state.hasNextEpisode) {
        PillButton("Next episode", onClick = viewModel::playNext, icon = PhosphorIcons.Bold.SkipForward)
    }
}

@Composable
private fun RoundButton(icon: ImageVector, description: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    FocusableSurface(
        onClick = onClick,
        shape = CircleShape,
        focusedScale = 1.12f,
        focusedContainerColor = Color.White,
        focusedContentColor = Color.Black,
        modifier = modifier.size(40.dp),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = description, modifier = Modifier.size(18.dp))
        }
    }
}
