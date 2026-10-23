package dev.neoworks.otter.ui.profile

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.roundToInt

/** The picked avatar, leaving [from] (null: where the PIN pad shows it) for the tab bar's Profile tab. */
data class Flight(val imageModel: String, val from: Rect?)

/**
 * The avatar's flight from the profile picker into the bottom bar's Profile tab, started by the
 * picker and played by [ProfileFlightOverlay]. Mirrors stores/profileFlight.svelte.ts.
 */
object ProfileFlight {

    private val mutableFlight = MutableStateFlow<Flight?>(null)
    val flight: StateFlow<Flight?> = mutableFlight

    /** Where the Profile tab shows the avatar, reported by the bottom bar. */
    private val mutableTargetBounds = MutableStateFlow<Rect?>(null)
    val targetBounds: StateFlow<Rect?> = mutableTargetBounds

    fun start(imageModel: String, from: Rect?) {
        mutableFlight.value = Flight(imageModel, from)
    }

    fun land() {
        mutableFlight.value = null
    }

    fun reportTargetBounds(bounds: Rect) {
        mutableTargetBounds.value = bounds
    }
}

private const val FLIGHT_MILLISECONDS = 1150
private const val WAIT_FOR_TARGET_MILLISECONDS = 600L
private val unlockAvatarSize = 96.dp
private const val UNLOCK_AVATAR_TOP_FRACTION = 0.3f

// Size of the avatar at the top of its arc, and where that apex sits.
private val apexSize = 64.dp
private const val APEX_X_FRACTION = 0.72f
private const val APEX_Y_FRACTION = 0.38f
private const val SHRINK_FRACTION = 0.6f

private class Keyframe(val offset: Float, val center: Offset, val size: Float, val easing: Easing)

/** Netflix's profile hand-off: the picked avatar shrinks in place, arcs up to the middle right, then drops into the tab bar. */
@Composable
fun ProfileFlightOverlay() {
    val flight by ProfileFlight.flight.collectAsStateWithLifecycle()
    val target by ProfileFlight.targetBounds.collectAsStateWithLifecycle()
    val current = flight ?: return
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val screen = Size(with(density) { maxWidth.toPx() }, with(density) { maxHeight.toPx() })
        val progress = remember(current) { Animatable(0f) }
        LaunchedEffect(current) {
            // Waits a moment for the app (and its bottom bar) to show behind the picker.
            val destination = waitForTarget()
            if (destination == null) {
                ProfileFlight.land()
                return@LaunchedEffect
            }
            progress.animateTo(1f, tween(FLIGHT_MILLISECONDS, easing = LinearEasing))
            ProfileFlight.land()
        }
        val destination = target ?: return@BoxWithConstraints
        var start = current.from
        if (start == null) {
            start = unlockAvatarRect(screen, with(density) { unlockAvatarSize.toPx() })
        }
        val frames = keyframes(start, destination, screen, with(density) { apexSize.toPx() })
        Traveller(current.imageModel, start, frames, progress::value)
    }
}

// Laid out once at the start rect and moved with a graphics layer, like the web version's transform,
// so the image isn't re-measured (or re-decoded) on every frame.
@Composable
private fun Traveller(imageModel: String, start: Rect, frames: List<Keyframe>, progress: () -> Float) {
    val density = LocalDensity.current
    val shape = RoundedCornerShape(16.dp)
    AsyncImage(
        model = imageModel,
        contentDescription = null,
        modifier = Modifier
            .offset { IntOffset(start.left.roundToInt(), start.top.roundToInt()) }
            .size(with(density) { start.width.toDp() }, with(density) { start.height.toDp() })
            .graphicsLayer {
                val frame = frameAt(progress(), frames)
                val scale = frame.second / start.width
                translationX = frame.first.x - start.center.x
                translationY = frame.first.y - start.center.y
                scaleX = scale
                scaleY = scale
                this.shape = shape
                clip = true
                shadowElevation = 24.dp.toPx() * scale
            },
    )
}

private suspend fun waitForTarget(): Rect? {
    val deadline = System.currentTimeMillis() + WAIT_FOR_TARGET_MILLISECONDS
    while (ProfileFlight.targetBounds.value == null && System.currentTimeMillis() < deadline) {
        delay(16)
    }
    return ProfileFlight.targetBounds.value
}

private fun unlockAvatarRect(screen: Size, avatarSize: Float): Rect {
    val left = (screen.width - avatarSize) / 2
    val top = screen.height * UNLOCK_AVATAR_TOP_FRACTION
    return Rect(left, top, left + avatarSize, top + avatarSize)
}

private fun keyframes(from: Rect, to: Rect, screen: Size, apexSizePixels: Float): List<Keyframe> {
    val apex = Offset(screen.width * APEX_X_FRACTION, screen.height * APEX_Y_FRACTION)
    return listOf(
        Keyframe(0f, from.center, from.width, CubicBezierEasing(0.4f, 0f, 0.2f, 1f)),
        Keyframe(0.22f, from.center, from.width * SHRINK_FRACTION, CubicBezierEasing(0.3f, 0f, 0.2f, 1f)),
        Keyframe(0.58f, apex, apexSizePixels, CubicBezierEasing(0.5f, 0f, 0.75f, 0.2f)),
        Keyframe(1f, to.center, to.width, LinearEasing),
    )
}
// Each segment eases with the easing of the keyframe it starts from.
private fun frameAt(progress: Float, frames: List<Keyframe>): Pair<Offset, Float> {
    val index = frames.indexOfLast { frame -> frame.offset <= progress }.coerceAtMost(frames.lastIndex - 1)
    val from = frames[index]
    val to = frames[index + 1]
    val local = from.easing.transform(((progress - from.offset) / (to.offset - from.offset)).coerceIn(0f, 1f))
    val center = Offset(from.center.x + (to.center.x - from.center.x) * local, from.center.y + (to.center.y - from.center.y) * local)
    return center to (from.size + (to.size - from.size) * local)
}
