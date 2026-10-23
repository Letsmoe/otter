package dev.neoworks.otter.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Regular
import com.adamglin.phosphoricons.regular.CardsThree
import com.adamglin.phosphoricons.regular.Gauge
import com.adamglin.phosphoricons.regular.SkipForward
import com.adamglin.phosphoricons.regular.Stack
import com.adamglin.phosphoricons.regular.Subtitles
import com.adamglin.phosphoricons.regular.UsersThree

private val bottomFade = Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f)))
private val previewFrameHeight = 112.dp
private val previewFrameWidth = 200.dp
private val previewLabelWidth = 96.dp

@Composable
fun PlayerBottomBar(
    state: PlayerUiState,
    position: PlaybackPosition,
    chrome: PlayerChrome,
    actions: PlayerActions,
    previewPlayer: () -> ExoPlayer,
    fadedAlpha: Float,
    modifier: Modifier,
) {
    Column(
        modifier
            .fillMaxWidth()
            .background(bottomFade)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal))
            .padding(start = 20.dp, end = 20.dp, top = 48.dp, bottom = 10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ScrubberWithPreview(state, position, chrome, actions, previewPlayer, Modifier.weight(1f))
            val remaining = (state.durationSeconds - position.positionSeconds).coerceAtLeast(0.0)
            Text(
                formatPlayerTime(remaining),
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                color = Color.White.copy(alpha = 0.8f),
                textAlign = TextAlign.End,
                modifier = Modifier.width(64.dp),
            )
        }
        ActionRow(state, actions, Modifier.alpha(fadedAlpha))
    }
}

@Composable
private fun ScrubberWithPreview(
    state: PlayerUiState,
    position: PlaybackPosition,
    chrome: PlayerChrome,
    actions: PlayerActions,
    previewPlayer: () -> ExoPlayer,
    modifier: Modifier,
) {
    BoxWithConstraints(modifier) {
        val previewSeconds = chrome.previewSeconds
        if (previewSeconds != null) {
            ScrubPreview(previewSeconds, state, maxWidth, previewPlayer, Modifier.align(Alignment.BottomStart))
        }
        PlayerScrubber(
            positionSeconds = position.positionSeconds,
            durationSeconds = state.durationSeconds,
            bufferedSeconds = position.bufferedSeconds,
            onSeek = actions.seek,
            onPreview = actions.preview,
        )
    }
}

// Centred over the finger, kept inside the bar: the frame at that time (direct files) and the time.
@Composable
private fun ScrubPreview(seconds: Double, state: PlayerUiState, barWidth: Dp, previewPlayer: () -> ExoPlayer, modifier: Modifier) {
    var width = previewLabelWidth
    if (state.previewable) {
        width = previewFrameWidth
    }
    var fraction = 0f
    if (state.durationSeconds > 0) {
        fraction = (seconds / state.durationSeconds).toFloat().coerceIn(0f, 1f)
    }
    val left = (barWidth * fraction - width / 2).coerceIn(0.dp, (barWidth - width).coerceAtLeast(0.dp))
    Column(
        modifier.offset(x = left, y = (-40).dp).width(width),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (state.previewable) {
            PreviewFrame(previewPlayer)
        }
        Text(formatPlayerTime(seconds), fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun PreviewFrame(previewPlayer: () -> ExoPlayer) {
    AndroidView(
        factory = { context ->
            PlayerView(context).apply {
                useController = false
                player = previewPlayer()
                setShutterBackgroundColor(android.graphics.Color.BLACK)
            }
        },
        modifier = Modifier.size(previewFrameWidth, previewFrameHeight).clip(RoundedCornerShape(6.dp)).background(Color.Black),
    )
}

@Composable
private fun ActionRow(state: PlayerUiState, actions: PlayerActions, modifier: Modifier) {
    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ActionButton(PhosphorIcons.Regular.Gauge, "Speed (${formatSpeed(state.speed)}x)") { actions.openSheet(PlayerSheetKind.SPEED) }
        if (state.hasTrackChoice) {
            ActionButton(PhosphorIcons.Regular.Subtitles, "Audio & Subtitles") { actions.openSheet(PlayerSheetKind.TRACKS) }
        }
        if (state.sources.size > 1) {
            ActionButton(PhosphorIcons.Regular.Stack, "Sources") { actions.openSheet(PlayerSheetKind.SOURCES) }
        }
        if (state.episodes.isNotEmpty()) {
            ActionButton(PhosphorIcons.Regular.CardsThree, "Episodes") { actions.openSheet(PlayerSheetKind.EPISODES) }
        }
        ActionButton(PhosphorIcons.Regular.UsersThree, "Together") { actions.openSheet(PlayerSheetKind.TOGETHER) }
        if (state.hasNextEpisode) {
            ActionButton(PhosphorIcons.Regular.SkipForward, "Next Ep.", actions.playNext)
        }
    }
}

fun formatSpeed(speed: Float): String {
    if (speed == speed.toInt().toFloat()) {
        return speed.toInt().toString()
    }
    return speed.toString()
}

@Composable
private fun ActionButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        Modifier.height(40.dp).clickableWithoutRipple(onClick = onClick).padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}
