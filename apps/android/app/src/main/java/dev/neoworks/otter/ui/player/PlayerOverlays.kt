package dev.neoworks.otter.ui.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Regular
import com.adamglin.phosphoricons.regular.ArrowClockwise
import com.adamglin.phosphoricons.regular.ArrowCounterClockwise
import dev.neoworks.otter.ui.together.PeerAvatar

enum class TapSide { LEFT, RIGHT }

/** A double tap's "±10s" bubble; [key] restarts its animation. */
data class SkipFeedback(val side: TapSide, val key: Long)

/** Under the controls: a single tap toggles them, a double tap on a side skips. */
@Composable
fun PlayerTapSurface(onToggleControls: () -> Unit, onDoubleTap: (TapSide) -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier.fillMaxSize().pointerInput(Unit) {
            detectTapGestures(
                onTap = { onToggleControls() },
                onDoubleTap = { offset ->
                    var side = TapSide.RIGHT
                    if (offset.x < size.width / 2) {
                        side = TapSide.LEFT
                    }
                    onDoubleTap(side)
                },
            )
        },
    )
}

@Composable
fun BoxScope.SkipFeedbackBubble(feedback: SkipFeedback) {
    val animation = remember(feedback.key) { Animatable(0f) }
    LaunchedEffect(feedback.key) { animation.animateTo(1f, tween(600)) }
    val progress = animation.value
    if (progress >= 1f) {
        return
    }
    var alignment = Alignment.CenterEnd
    var icon = PhosphorIcons.Regular.ArrowClockwise
    var label = "+$SKIP_SECONDS"
    var startPadding = 0.dp
    var endPadding = 96.dp
    if (feedback.side == TapSide.LEFT) {
        alignment = Alignment.CenterStart
        icon = PhosphorIcons.Regular.ArrowCounterClockwise
        label = "−$SKIP_SECONDS"
        startPadding = 96.dp
        endPadding = 0.dp
    }
    Column(
        Modifier
            .align(alignment)
            .padding(start = startPadding, end = endPadding)
            .size(80.dp)
            .scale(0.8f + 0.2f * (progress / 0.2f).coerceAtMost(1f))
            .alpha(feedbackAlpha(progress))
            .playerGlass(CircleShape),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp))
        Text("${label}s", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}

// Pops in over the first 20%, then fades out.
private fun feedbackAlpha(progress: Float): Float {
    if (progress < 0.2f) {
        return progress / 0.2f
    }
    return 1f - (progress - 0.2f) / 0.8f
}

/** Watch Together is waiting for the others to load the title. */
@Composable
fun HoldingNotice(state: PlayerUiState, onStartWithoutWaiting: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier.playerGlass(RoundedCornerShape(24.dp)).padding(horizontal = 24.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy((-8).dp)) {
            for (peer in state.waitingFor) {
                PeerAvatar(peer, 40.dp, buffering = true)
            }
        }
        Text(waitingText(state), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
        Box(
            Modifier.height(32.dp).background(Color.White.copy(alpha = 0.1f), CircleShape).clickableWithoutRipple(onClick = onStartWithoutWaiting).padding(horizontal = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text("Start without them", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

private fun waitingText(state: PlayerUiState): String {
    val names = state.waitingFor.map { peer -> peer.name }
    if (names.size <= 2) {
        return "Waiting for ${names.joinToString(" and ")}…"
    }
    return "Waiting for ${names.size} people…"
}

@Composable
fun BufferingIndicator(state: PlayerUiState, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        CircularProgressIndicator(color = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(48.dp))
        if (!state.hasSource) {
            Text(state.statusText, fontSize = 14.sp, color = Color.White.copy(alpha = 0.7f))
        }
    }
}

@Composable
fun PlayerErrorNotice(message: String, onChooseAnotherSource: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(message, fontSize = 14.sp, color = Color.White.copy(alpha = 0.8f))
        Box(Modifier.height(40.dp).playerGlass(CircleShape).clickableWithoutRipple(onClick = onChooseAnotherSource).padding(horizontal = 20.dp), contentAlignment = Alignment.Center) {
            Text("Choose another source", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}
