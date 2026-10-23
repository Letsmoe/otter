package dev.neoworks.otter.ui.player

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private val restingTrackHeight = 3.dp
private val draggingTrackHeight = 6.dp
private val restingThumbSize = 14.dp
private val draggingThumbSize = 34.dp

/**
 * Draggable progress bar: buffered range, played range and a small glass thumb that swells (and
 * the track thickens) while dragging. Reports the target time on release. Mirrors Scrubber.svelte.
 */
@Composable
fun PlayerScrubber(
    positionSeconds: Double,
    durationSeconds: Double,
    bufferedSeconds: Double,
    onSeek: (Double) -> Unit,
    onPreview: (Double?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var dragSeconds by remember { mutableStateOf<Double?>(null) }
    val dragging = dragSeconds != null
    val shownSeconds = dragSeconds ?: positionSeconds
    val trackHeight by animateDpAsState(trackHeightFor(dragging), tween(180), label = "scrubTrack")
    val thumbSize by animateDpAsState(thumbSizeFor(dragging), spring(0.55f, Spring.StiffnessMediumLow), label = "scrubThumb")
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(32.dp)
            .semantics { contentDescription = "Seek" }
            .scrubGestures(durationSeconds, onDrag = { seconds -> dragSeconds = seconds; onPreview(seconds) }) { seconds ->
                dragSeconds = null
                onSeek(seconds)
                onPreview(null)
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        val playedFraction = fractionOf(shownSeconds, durationSeconds)
        ScrubTrack(trackHeight, fractionOf(bufferedSeconds, durationSeconds), playedFraction)
        val thumbOffset = maxWidth * playedFraction - thumbSize / 2
        Box(
            Modifier
                .offset(x = thumbOffset)
                .size(thumbSize)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.45f))
                .border(1.dp, playerRimColor, CircleShape),
        )
    }
}

@Composable
private fun ScrubTrack(height: Dp, bufferedFraction: Float, playedFraction: Float) {
    Box(Modifier.fillMaxWidth().height(height).clip(CircleShape).background(Color.White.copy(alpha = 0.2f))) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(bufferedFraction).background(Color.White.copy(alpha = 0.3f)))
        Box(Modifier.fillMaxHeight().fillMaxWidth(playedFraction).background(Color.White))
    }
}

private fun trackHeightFor(dragging: Boolean): Dp {
    if (dragging) {
        return draggingTrackHeight
    }
    return restingTrackHeight
}

private fun thumbSizeFor(dragging: Boolean): Dp {
    if (dragging) {
        return draggingThumbSize
    }
    return restingThumbSize
}

private fun fractionOf(seconds: Double, durationSeconds: Double): Float {
    if (durationSeconds <= 0 || durationSeconds.isInfinite()) {
        return 0f
    }
    return (seconds / durationSeconds).toFloat().coerceIn(0f, 1f)
}

// Down and drag report the time under the finger; lifting reports where to seek.
private fun Modifier.scrubGestures(
    durationSeconds: Double,
    onDrag: (Double) -> Unit,
    onRelease: (Double) -> Unit,
): Modifier = pointerInput(durationSeconds) {
    awaitEachGesture {
        val down = awaitFirstDown()
        var seconds = secondsAt(down.position.x, size.width, durationSeconds)
        onDrag(seconds)
        down.consume()
        do {
            val event = awaitPointerEvent()
            val change = event.changes.firstOrNull { it.id == down.id }
            if (change != null && change.pressed) {
                seconds = secondsAt(change.position.x, size.width, durationSeconds)
                onDrag(seconds)
                change.consume()
            }
        } while (event.changes.any { it.pressed })
        onRelease(seconds)
    }
}

private fun secondsAt(x: Float, width: Int, durationSeconds: Double): Double {
    if (width <= 0) {
        return 0.0
    }
    return (x / width).coerceIn(0f, 1f) * durationSeconds
}
