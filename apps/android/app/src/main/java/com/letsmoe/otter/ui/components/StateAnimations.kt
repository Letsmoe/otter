package com.letsmoe.otter.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

// Bouncy enough to overshoot a couple of times, quick enough not to feel slow.
private const val BOUNCE_DAMPING = 0.38f
private const val BOUNCE_STIFFNESS = 420f
private const val SQUASH_MILLISECONDS = 90
private const val JUMP_UP_MILLISECONDS = 170
private const val RING_MILLISECONDS = 850
private const val WIGGLE_MILLISECONDS = 420
private val defaultJumpHeight = 14.dp

/**
 * Runs [animation] each time [state] changes after the first composition, so a button only
 * moves when the user changes something, not when the page opens.
 */
@Composable
private fun OnStateChange(state: Any?, animation: suspend CoroutineScope.(Any?) -> Unit) {
    var previous by remember { mutableStateOf(state) }
    LaunchedEffect(state) {
        if (previous == state) {
            return@LaunchedEffect
        }
        previous = state
        animation(state)
    }
}

/** Squashes wide and flat, then springs back taller and settles, whenever [state] changes. */
@Composable
fun Modifier.squishOnChange(state: Any?): Modifier {
    val scaleX = remember { Animatable(1f) }
    val scaleY = remember { Animatable(1f) }
    OnStateChange(state) {
        launch {
            scaleX.animateTo(1.28f, tween(SQUASH_MILLISECONDS))
            scaleX.animateTo(1f, spring(BOUNCE_DAMPING, BOUNCE_STIFFNESS))
        }
        scaleY.animateTo(0.72f, tween(SQUASH_MILLISECONDS))
        scaleY.animateTo(1f, spring(BOUNCE_DAMPING, BOUNCE_STIFFNESS))
    }
    return graphicsLayer {
        this.scaleX = scaleX.value
        this.scaleY = scaleY.value
    }
}

/** Swings like a ringing bell, hanging from its top, when [ringing] turns true. */
@Composable
fun Modifier.ringOnChange(ringing: Boolean): Modifier {
    val angle = remember { Animatable(0f) }
    OnStateChange(ringing) { current ->
        if (current != true) {
            return@OnStateChange
        }
        angle.snapTo(0f)
        angle.animateTo(0f, keyframes {
            durationMillis = RING_MILLISECONDS
            -30f at 90
            26f at 200
            -20f at 310
            14f at 420
            -9f at 530
            5f at 640
        })
    }
    return graphicsLayer {
        rotationZ = angle.value
        transformOrigin = TransformOrigin(0.5f, 0.1f)
    }
}

/** Wiggles side to side around its centre whenever [state] changes (e.g. a tap counter). */
@Composable
fun Modifier.wiggleOnChange(state: Any?): Modifier {
    val angle = remember { Animatable(0f) }
    OnStateChange(state) {
        angle.snapTo(0f)
        angle.animateTo(0f, keyframes {
            durationMillis = WIGGLE_MILLISECONDS
            -16f at 70
            13f at 160
            -8f at 250
            4f at 330
        })
    }
    return graphicsLayer { rotationZ = angle.value }
}

/** Grows in from nothing with an overshoot the first time it's shown (status dots). */
@Composable
fun Modifier.popIn(): Modifier {
    val scale = remember { Animatable(0f) }
    LaunchedEffect(Unit) { scale.animateTo(1f, spring(BOUNCE_DAMPING, BOUNCE_STIFFNESS)) }
    return graphicsLayer {
        scaleX = scale.value
        scaleY = scale.value
    }
}

/** Jumps up with a little tilt and lands with a bounce when [state] changes to a non-null value. */
@Composable
fun Modifier.jumpOnChange(state: Any?, height: Dp = defaultJumpHeight): Modifier {
    val heightPixels = with(LocalDensity.current) { height.toPx() }
    val lift = remember { Animatable(0f) }
    val tilt = remember { Animatable(0f) }
    OnStateChange(state) { current ->
        if (current == null) {
            return@OnStateChange
        }
        launch {
            tilt.animateTo(-14f, tween(JUMP_UP_MILLISECONDS))
            tilt.animateTo(0f, spring(BOUNCE_DAMPING, BOUNCE_STIFFNESS))
        }
        lift.animateTo(-heightPixels, tween(JUMP_UP_MILLISECONDS, easing = FastOutSlowInEasing))
        lift.animateTo(0f, spring(BOUNCE_DAMPING, BOUNCE_STIFFNESS))
    }
    return graphicsLayer {
        translationY = lift.value
        rotationZ = tilt.value
    }
}
