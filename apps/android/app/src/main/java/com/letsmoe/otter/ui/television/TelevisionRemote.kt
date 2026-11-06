package com.letsmoe.otter.ui.television

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.letsmoe.otter.cast.TelevisionConnection
import com.letsmoe.otter.cast.TelevisionTarget
import com.letsmoe.otter.ui.components.GlassBottomSheet
import com.letsmoe.otter.ui.components.PillButton
import com.letsmoe.otter.ui.player.PlaybackPosition
import com.letsmoe.otter.ui.player.PlayerRequest
import com.letsmoe.otter.ui.player.PlayerUiState
import com.letsmoe.otter.ui.player.PlayerViewModel
import com.letsmoe.otter.ui.player.SourcesSheetContent
import com.letsmoe.otter.ui.player.SpeedSheetContent
import com.letsmoe.otter.ui.player.TelevisionPlayback
import com.letsmoe.otter.ui.player.TracksSheetContent
import kotlinx.coroutines.delay

private const val SKIP_SECONDS = 10
// Volume changes are sent once the finger rests this long, not on every slider step.
private const val VOLUME_SEND_DELAY_MILLISECONDS = 150L

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
            TracksSheetContent(state, viewModel::chooseAudio, viewModel::chooseSubtitle, viewModel::toggleVoiceBoost)
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
        Text(
            "Disconnect",
            fontSize = 14.sp,
            color = Color.White.copy(alpha = 0.7f),
            modifier = Modifier.clickable(onClick = TelevisionPlayback::disconnect).padding(8.dp),
        )
    }
}

@Composable
private fun NowPlaying(state: PlayerUiState) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(state.title, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        val status = statusLine(state) ?: return
        Text(status, fontSize = 13.sp, color = Color.White.copy(alpha = 0.6f))
    }
}

private fun statusLine(state: PlayerUiState): String? {
    val error = state.shownError
    if (error != null) {
        return error
    }
    if (!state.hasSource) {
        return state.statusText
    }
    if (state.isBuffering) {
        return "Loading on the TV…"
    }
    return null
}

@Composable
private fun RemoteScrubber(position: PlaybackPosition, durationSeconds: Double, onSeek: (Double) -> Unit) {
    var dragging by remember { mutableStateOf(false) }
    var draggedSeconds by remember { mutableFloatStateOf(0f) }
    val duration = durationSeconds.toFloat().coerceAtLeast(1f)
    var shownSeconds = position.positionSeconds.toFloat()
    if (dragging) {
        shownSeconds = draggedSeconds
    }
    Column {
        Slider(
            value = shownSeconds.coerceIn(0f, duration),
            onValueChange = { seconds ->
                dragging = true
                draggedSeconds = seconds
            },
            onValueChangeFinished = {
                dragging = false
                onSeek(draggedSeconds.toDouble())
            },
            valueRange = 0f..duration,
            colors = remoteSliderColors(),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatClock(shownSeconds.toDouble()), fontSize = 12.sp, color = Color.White.copy(alpha = 0.6f))
            Text(formatClock(durationSeconds), fontSize = 12.sp, color = Color.White.copy(alpha = 0.6f))
        }
    }
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
    if (state.hasNextEpisode) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            Row(
                Modifier.clickable(onClick = viewModel::playNext).padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(PhosphorIcons.Regular.SkipForward, contentDescription = null, modifier = Modifier.size(18.dp))
                Text("Next episode", fontSize = 14.sp)
            }
        }
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
    Box(Modifier.size(sizeDp.dp).background(fill, CircleShape).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
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
        Slider(
            value = shown,
            onValueChange = { changed ->
                volume = changed
                pendingVolume = changed
            },
            colors = remoteSliderColors(),
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
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 14.dp),
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
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PillButton("Stop", Modifier.weight(1f), enabled = true, filled = false, onClick = TelevisionPlayback::stop)
        PillButton("Continue on phone", Modifier.weight(1f), enabled = true, filled = true) {
            val request = TelevisionPlayback.continueOnPhone() ?: return@PillButton
            onContinueOnPhone(request)
        }
    }
}

@Composable
private fun ColumnScope.SubPage(onBack: () -> Unit, content: @Composable () -> Unit) {
    Text(
        "‹ Back",
        fontSize = 14.sp,
        color = Color.White.copy(alpha = 0.7f),
        modifier = Modifier.clickable(onClick = onBack).padding(horizontal = 24.dp, vertical = 12.dp),
    )
    Box(Modifier.weight(1f)) { content() }
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
