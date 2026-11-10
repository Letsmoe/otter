package com.letsmoe.otter.ui.television

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Fill
import com.adamglin.phosphoricons.Regular
import com.adamglin.phosphoricons.fill.Pause
import com.adamglin.phosphoricons.fill.Play
import com.adamglin.phosphoricons.regular.ArrowClockwise
import com.adamglin.phosphoricons.regular.ArrowCounterClockwise
import com.adamglin.phosphoricons.regular.CaretRight
import com.adamglin.phosphoricons.regular.SkipForward
import com.adamglin.phosphoricons.regular.SpeakerHigh
import com.adamglin.phosphoricons.regular.Television
import com.adamglin.phosphoricons.regular.WarningCircle
import com.letsmoe.otter.cast.TelevisionConnection
import com.letsmoe.otter.cast.TelevisionTarget
import com.letsmoe.otter.ui.components.GlassBottomSheet
import com.letsmoe.otter.ui.components.PRESSED_SCALE_LARGE
import com.letsmoe.otter.ui.components.PillButton
import com.letsmoe.otter.ui.components.SubtleFill
import com.letsmoe.otter.ui.theme.OtterColors
import com.letsmoe.otter.ui.components.pressable
import com.letsmoe.otter.ui.player.PlaybackPosition
import com.letsmoe.otter.ui.player.PlayerRequest
import com.letsmoe.otter.ui.player.PlayerUiState
import com.letsmoe.otter.ui.player.PlayerViewModel
import com.letsmoe.otter.ui.player.SourcesSheetContent
import com.letsmoe.otter.ui.player.SpeedSheetContent
import com.letsmoe.otter.ui.player.TelevisionPlayback
import com.letsmoe.otter.ui.player.sourceStatusText
import com.letsmoe.otter.vpn.VpnStatus
import com.letsmoe.otter.vpn.VpnStatusStore
import kotlinx.coroutines.delay
import kotlin.math.abs

private const val SKIP_SECONDS = 10
// Volume changes are sent once the finger rests this long, not on every slider step.
private const val VOLUME_SEND_DELAY_MILLISECONDS = 150L
// After a seek the thumb stays where the user let go until the TV reports a position this close,
// or for this long: a TV takes seconds to get there and would otherwise pull the thumb back.
private const val SEEK_ARRIVED_SECONDS = 5f
private const val SEEK_HOLD_MILLISECONDS = 15_000L
private val SLIDER_TRACK_HEIGHT = 6.dp
private val SLIDER_THUMB_SIZE = 18.dp
private val SLIDER_THUMB_TOUCHED_SIZE = 26.dp

private enum class RemotePage { MAIN, CAPTIONS, SOURCES, SPEED }

/** The remote for the connected TV, over everything; [onContinueOnPhone] opens the player screen. */
@Composable
fun TelevisionRemoteHost(onContinueOnPhone: (PlayerRequest) -> Unit) {
    val open by TelevisionPlayback.remoteOpen.collectAsStateWithLifecycle()
    val target by TelevisionConnection.target.collectAsStateWithLifecycle()
    val connected = target ?: return
    if (!open) {
        return
    }
    GlassBottomSheet(onDismiss = TelevisionPlayback::closeRemote, fullHeight = true) {
        RemoteContent(connected, onContinueOnPhone)
    }
}

@Composable
private fun ColumnScope.RemoteContent(target: TelevisionTarget, onContinueOnPhone: (PlayerRequest) -> Unit) {
    val playback by TelevisionPlayback.player.collectAsStateWithLifecycle()
    var page by remember { mutableStateOf(RemotePage.MAIN) }
    val viewModel = playback
    if (viewModel == null) {
        IdleRemote(target)
        return
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val position by viewModel.position.collectAsStateWithLifecycle()
    when (page) {
        RemotePage.MAIN -> PlayingRemote(target, viewModel, state, position, onOpen = { chosen -> page = chosen }, onContinueOnPhone = onContinueOnPhone)
        RemotePage.CAPTIONS -> SubPage(onBack = { page = RemotePage.MAIN }) {
            TelevisionCaptionsContent(state, viewModel::chooseAudio, viewModel::chooseSubtitle)
        }
        RemotePage.SOURCES -> SubPage(onBack = { page = RemotePage.MAIN }) {
            SourcesSheetContent(state.sources, state.currentSourceId, state.searchingSources, { sourceId ->
                page = RemotePage.MAIN
                viewModel.chooseSource(sourceId)
            }, viewModel::searchSourcesAgain)
        }
        RemotePage.SPEED -> SubPage(onBack = { page = RemotePage.MAIN }) {
            SpeedSheetContent(state.speed, viewModel::chooseSpeed)
        }
    }
}

@Composable
private fun IdleRemote(target: TelevisionTarget) {
    Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        ConnectionHeader(target)
        Text("Pick a title and press Play: it starts on the TV.", fontSize = 15.sp, color = Color.White.copy(alpha = 0.7f))
        VolumeRow()
    }
}

@Composable
private fun PlayingRemote(
    target: TelevisionTarget,
    viewModel: PlayerViewModel,
    state: PlayerUiState,
    position: PlaybackPosition,
    onOpen: (RemotePage) -> Unit,
    onContinueOnPhone: (PlayerRequest) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        ConnectionHeader(target)
        NowPlaying(state)
        if (state.shownError != null) {
            ErrorActions(onRetry = viewModel::retryAfterError, onOtherSource = { onOpen(RemotePage.SOURCES) })
        }
        RemoteScrubber(position, state.durationSeconds, viewModel::seekTo)
        TransportRow(state, viewModel)
        VolumeRow()
        RemoteSettings(state, onOpen)
        RemoteActions(onContinueOnPhone)
    }
}

@Composable
private fun ConnectionHeader(target: TelevisionTarget) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(PhosphorIcons.Regular.Television, contentDescription = null, modifier = Modifier.size(22.dp))
        Text(target.name, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        PillButton("Disconnect", Modifier, enabled = true, filled = false, onClick = TelevisionPlayback::disconnect)
    }
}

@Composable
private fun NowPlaying(state: PlayerUiState) {
    val vpn by VpnStatusStore.status.collectAsStateWithLifecycle()
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(state.title, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        val status = remoteStatus(state, vpn) ?: return
        StatusChip(status)
    }
}

@Composable
private fun ErrorActions(onRetry: () -> Unit, onOtherSource: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PillButton("Try again", Modifier, enabled = true, filled = true, onClick = onRetry)
        PillButton("Other source", Modifier, enabled = true, filled = false, onClick = onOtherSource)
    }
}

/** What the remote says under the title; [busy] shows a spinner, [problem] the warning look. */
private class RemoteStatus(val text: String, val busy: Boolean, val problem: Boolean)

private fun remoteStatus(state: PlayerUiState, vpn: VpnStatus): RemoteStatus? {
    val error = state.shownError
    if (error != null) {
        return RemoteStatus(error, busy = false, problem = true)
    }
    if (!state.hasSource) {
        val blocked = vpn.blocksStreaming
        return RemoteStatus(sourceStatusText(state, vpn), busy = !blocked, problem = blocked)
    }
    if (state.isBuffering) {
        return RemoteStatus("Loading on the TV…", busy = true, problem = false)
    }
    return null
}

@Composable
private fun StatusChip(status: RemoteStatus) {
    var tint = Color.White.copy(alpha = 0.75f)
    if (status.problem) {
        tint = OtterColors.warning
    }
    Row(
        Modifier.background(SubtleFill, CircleShape).padding(horizontal = 14.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (status.busy) {
            CircularProgressIndicator(Modifier.size(14.dp), color = tint, strokeWidth = 2.dp)
        }
        if (status.problem) {
            Icon(PhosphorIcons.Regular.WarningCircle, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
        }
        Text(status.text, fontSize = 13.sp, color = tint)
    }
}

@Composable
private fun RemoteScrubber(position: PlaybackPosition, durationSeconds: Double, onSeek: (Double) -> Unit) {
    var dragging by remember { mutableStateOf(false) }
    var draggedSeconds by remember { mutableFloatStateOf(0f) }
    // Where the user let go, shown until the TV reports it got there.
    var seekTargetSeconds by remember { mutableStateOf<Float?>(null) }
    val reportedSeconds = position.positionSeconds.toFloat()
    LaunchedEffect(seekTargetSeconds, reportedSeconds) {
        val target = seekTargetSeconds ?: return@LaunchedEffect
        if (abs(reportedSeconds - target) < SEEK_ARRIVED_SECONDS) {
            seekTargetSeconds = null
            return@LaunchedEffect
        }
        delay(SEEK_HOLD_MILLISECONDS)
        seekTargetSeconds = null
    }
    val duration = durationSeconds.toFloat().coerceAtLeast(1f)
    val shownSeconds = scrubberSeconds(dragging, draggedSeconds, seekTargetSeconds, reportedSeconds)
    Column {
        RemoteSlider(
            value = shownSeconds.coerceIn(0f, duration),
            onValueChange = { seconds ->
                dragging = true
                draggedSeconds = seconds
            },
            onValueChangeFinished = {
                dragging = false
                seekTargetSeconds = draggedSeconds
                onSeek(draggedSeconds.toDouble())
            },
            valueRange = 0f..duration,
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatClock(shownSeconds.toDouble()), fontSize = 12.sp, color = Color.White.copy(alpha = 0.6f))
            Text(formatClock(durationSeconds), fontSize = 12.sp, color = Color.White.copy(alpha = 0.6f))
        }
    }
}

private fun scrubberSeconds(dragging: Boolean, draggedSeconds: Float, seekTargetSeconds: Float?, reportedSeconds: Float): Float {
    if (dragging) {
        return draggedSeconds
    }
    if (seekTargetSeconds != null) {
        return seekTargetSeconds
    }
    return reportedSeconds
}

@Composable
private fun TransportRow(state: PlayerUiState, viewModel: PlayerViewModel) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
        RoundIconButton(PhosphorIcons.Regular.ArrowCounterClockwise, "Back $SKIP_SECONDS seconds", 56) { viewModel.skip(-SKIP_SECONDS) }
        var playIcon = PhosphorIcons.Fill.Play
        var playLabel = "Play"
        if (state.isPlaying) {
            playIcon = PhosphorIcons.Fill.Pause
            playLabel = "Pause"
        }
        RoundIconButton(playIcon, playLabel, 76, filled = true, onClick = viewModel::togglePlay)
        RoundIconButton(PhosphorIcons.Regular.ArrowClockwise, "Forward $SKIP_SECONDS seconds", 56) { viewModel.skip(SKIP_SECONDS) }
    }
    JumpRow(state, viewModel)
}

// "Skip Intro" / "Skip Recap" while one plays on the TV, and the next episode.
@Composable
private fun JumpRow(state: PlayerUiState, viewModel: PlayerViewModel) {
    val skipJump by viewModel.skipButton.collectAsStateWithLifecycle()
    val jump = skipJump
    if (jump == null && !state.hasNextEpisode) {
        return
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
        if (jump != null) {
            JumpPill(jump.label, onClick = viewModel::skipSegment)
        }
        if (state.hasNextEpisode) {
            JumpPill("Next episode", onClick = viewModel::playNext)
        }
    }
}

@Composable
private fun JumpPill(label: String, onClick: () -> Unit) {
    Row(
        Modifier
            .pressable(onClick = onClick)
            .background(Color.White.copy(alpha = 0.08f), CircleShape)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(PhosphorIcons.Regular.SkipForward, contentDescription = null, modifier = Modifier.size(18.dp))
        Text(label, fontSize = 14.sp)
    }
}

@Composable
private fun RoundIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    sizeDp: Int,
    filled: Boolean = false,
    onClick: () -> Unit,
) {
    var fill = Color.White.copy(alpha = 0.08f)
    var tint = Color.White
    if (filled) {
        fill = Color.White
        tint = Color.Black
    }
    Box(Modifier.pressable(onClick = onClick).size(sizeDp.dp).background(fill, CircleShape), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size((sizeDp * 0.42f).dp))
    }
}

/** The TV's own volume; shown only when the TV lets it be set. */
@Composable
private fun VolumeRow() {
    if (!TelevisionConnection.supportsVolume) {
        return
    }
    var volume by remember { mutableStateOf<Float?>(null) }
    var pendingVolume by remember { mutableStateOf<Float?>(null) }
    LaunchedEffect(Unit) { volume = TelevisionConnection.volume() }
    LaunchedEffect(pendingVolume) {
        val target = pendingVolume ?: return@LaunchedEffect
        delay(VOLUME_SEND_DELAY_MILLISECONDS)
        TelevisionConnection.setVolume(target)
    }
    val shown = volume ?: return
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(PhosphorIcons.Regular.SpeakerHigh, contentDescription = "Volume", modifier = Modifier.size(20.dp))
        RemoteSlider(
            value = shown,
            onValueChange = { changed ->
                volume = changed
                pendingVolume = changed
            },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun RemoteSettings(state: PlayerUiState, onOpen: (RemotePage) -> Unit) {
    Column(
        Modifier.fillMaxWidth().background(Color.White.copy(alpha = 0.06f), RoundedCornerShape(20.dp)).padding(horizontal = 16.dp),
    ) {
        SettingsRow("Captions", captionLabel(state)) { onOpen(RemotePage.CAPTIONS) }
        SettingsRow("Source & language", sourceLabel(state)) { onOpen(RemotePage.SOURCES) }
        // DLNA TVs play at normal speed only.
        if (!state.castsToTelevision) {
            SettingsRow("Speed", "${state.speed}×") { onOpen(RemotePage.SPEED) }
        }
    }
}

private fun captionLabel(state: PlayerUiState): String {
    val selected = state.subtitleTracks.firstOrNull { track -> track.index == state.selectedSubtitleIndex } ?: return "Off"
    return selected.label
}

private fun sourceLabel(state: PlayerUiState): String {
    val current = state.sources.firstOrNull { source -> source.id == state.currentSourceId } ?: return "…"
    return current.label
}

@Composable
private fun SettingsRow(title: String, value: String, onClick: () -> Unit) {
    Row(
        Modifier.pressable(pressedScale = PRESSED_SCALE_LARGE, onClick = onClick).fillMaxWidth().padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Text(value, fontSize = 14.sp, color = Color.White.copy(alpha = 0.6f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Icon(PhosphorIcons.Regular.CaretRight, contentDescription = null, modifier = Modifier.size(16.dp))
    }
}

@Composable
private fun RemoteActions(onContinueOnPhone: (PlayerRequest) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
        PillButton("Stop", Modifier, enabled = true, filled = false, onClick = TelevisionPlayback::stop)
        PillButton("Continue on phone", Modifier, enabled = true, filled = true) {
            val request = TelevisionPlayback.continueOnPhone() ?: return@PillButton
            onContinueOnPhone(request)
        }
    }
}

@Composable
private fun ColumnScope.SubPage(onBack: () -> Unit, content: @Composable () -> Unit) {
    PillButton("‹ Back", Modifier.padding(start = 24.dp, top = 12.dp), enabled = true, filled = false, onClick = onBack)
    Box(Modifier.weight(1f)) { content() }
}

/** A slider with a round thumb that grows under the finger and a thick track without gaps. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RemoteSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    onValueChangeFinished: (() -> Unit)? = null,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val colors = remoteSliderColors()
    Slider(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        onValueChangeFinished = onValueChangeFinished,
        valueRange = valueRange,
        colors = colors,
        interactionSource = interactionSource,
        thumb = { RemoteSliderThumb(interactionSource) },
        track = { sliderState ->
            SliderDefaults.Track(
                sliderState = sliderState,
                colors = colors,
                drawStopIndicator = null,
                thumbTrackGapSize = 0.dp,
                modifier = Modifier.height(SLIDER_TRACK_HEIGHT),
            )
        },
    )
}

@Composable
private fun RemoteSliderThumb(interactionSource: MutableInteractionSource) {
    val pressed by interactionSource.collectIsPressedAsState()
    val dragged by interactionSource.collectIsDraggedAsState()
    var targetSize = SLIDER_THUMB_SIZE
    if (pressed || dragged) {
        targetSize = SLIDER_THUMB_TOUCHED_SIZE
    }
    val size by animateDpAsState(targetSize, spring(dampingRatio = Spring.DampingRatioMediumBouncy), label = "thumbSize")
    Box(Modifier.size(SLIDER_THUMB_TOUCHED_SIZE), contentAlignment = Alignment.Center) {
        Box(Modifier.size(size).background(Color.White, CircleShape))
    }
}

@Composable
private fun remoteSliderColors() = SliderDefaults.colors(
    thumbColor = Color.White,
    activeTrackColor = Color.White,
    inactiveTrackColor = Color.White.copy(alpha = 0.2f),
)

/** "1:02:03" or "2:03". */
fun formatClock(seconds: Double): String {
    val total = seconds.toLong().coerceAtLeast(0)
    val hours = total / 3600
    val minutes = (total % 3600) / 60
    val rest = total % 60
    if (hours > 0) {
        return "%d:%02d:%02d".format(hours, minutes, rest)
    }
    return "%d:%02d".format(minutes, rest)
}
