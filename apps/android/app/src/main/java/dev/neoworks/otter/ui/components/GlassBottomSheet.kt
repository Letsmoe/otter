package dev.neoworks.otter.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val sheetShape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)
private const val SCRIM_ALPHA = 0.6f
private const val MAX_HEIGHT_FRACTION = 0.9f

// A drag past this share of the sheet's height, or a fling faster than this, closes it.
private const val DISMISS_DRAG_FRACTION = 0.3f
private const val DISMISS_VELOCITY = 1800f

/**
 * Glass sheet sliding up from the bottom over a dimmed page; dragging it down, tapping the dim or
 * going back closes it. Mirrors BottomSheet.svelte. Rendered by the app's [SheetHostOutlet] so its
 * glass blurs the page behind it.
 */
@Composable
fun GlassBottomSheet(onDismiss: () -> Unit, fullHeight: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    val host = LocalSheetHost.current
    if (host == null) {
        GlassSheetLayer(SheetEntry(onDismiss, fullHeight, content))
        return
    }
    RegisterSheet(host, onDismiss, fullHeight, content)
}

/** One sheet with its scrim, slide-in, drag-to-close and back handling. */
@Composable
internal fun GlassSheetLayer(entry: SheetEntry) {
    val scope = rememberCoroutineScope()
    val motion = remember { SheetMotion() }
    LaunchedEffect(Unit) { motion.reveal.animateTo(1f, spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessMediumLow)) }
    val close = { scope.launch { motion.closeThen(entry.onDismiss) }; Unit }
    BackHandler(onBack = close)
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val maxSheetHeight = maxHeight * MAX_HEIGHT_FRACTION
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = motion.reveal.value }
                .background(Color.Black.copy(alpha = SCRIM_ALPHA))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = close),
        )
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .offset { IntOffset(0, motion.offsetPixels().roundToInt()) }
                .onSizeChanged { size -> motion.sheetHeight = size.height.toFloat() }
                .fillMaxWidth()
                .sheetHeight(entry.fullHeight, maxSheetHeight)
                .frostedGlass(sheetShape)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = {})
                .sheetDrag(motion, scope, entry.onDismiss),
        ) {
            SheetHandle()
            SheetContent(entry)
        }
    }
}

private fun Modifier.sheetHeight(fullHeight: Boolean, maxSheetHeight: Dp): Modifier {
    if (fullHeight) {
        return height(maxSheetHeight)
    }
    return heightIn(max = maxSheetHeight)
}

// Full-height sheets lay out their own scrolling (e.g. a chat with its field pinned at the bottom);
// the rest scroll as a whole. Both stay above the keyboard and the navigation bar.
@Composable
private fun ColumnScope.SheetContent(entry: SheetEntry) {
    val insets = Modifier.windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars))
    var content = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
    if (entry.fullHeight) {
        content = Modifier.fillMaxWidth().weight(1f)
    }
    Column(
        modifier = content.padding(start = 20.dp, end = 20.dp, bottom = 20.dp).then(insets),
        content = entry.content,
    )
}

/** Slide-in progress plus the distance the user has dragged the sheet down. */
private class SheetMotion {
    val reveal = Animatable(0f)
    val drag = Animatable(0f)
    var sheetHeight by mutableFloatStateOf(0f)

    fun offsetPixels(): Float = sheetHeight * (1f - reveal.value) + drag.value

    suspend fun closeThen(onDismiss: () -> Unit) {
        reveal.animateTo(0f, tween(220))
        onDismiss()
    }
}

@Composable
private fun Modifier.sheetDrag(motion: SheetMotion, scope: CoroutineScope, onDismiss: () -> Unit): Modifier = draggable(
    orientation = Orientation.Vertical,
    state = rememberDraggableStateFor(motion, scope),
    onDragStopped = { velocity ->
        val draggedFar = motion.drag.value > motion.sheetHeight * DISMISS_DRAG_FRACTION
        if (draggedFar || velocity > DISMISS_VELOCITY) {
            motion.closeThen(onDismiss)
            motion.drag.snapTo(0f)
            return@draggable
        }
        motion.drag.animateTo(0f, spring(dampingRatio = 0.8f))
    },
)

@Composable
private fun rememberDraggableStateFor(motion: SheetMotion, scope: CoroutineScope) = rememberDraggableState { delta ->
    scope.launch { motion.drag.snapTo((motion.drag.value + delta).coerceAtLeast(0f)) }
}

@Composable
private fun SheetHandle() {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .padding(top = 12.dp, bottom = 12.dp)
                .width(40.dp)
                .height(4.dp)
                .background(Color.White.copy(alpha = 0.3f), CircleShape),
        )
    }
}
