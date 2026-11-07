package com.letsmoe.otter.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import kotlinx.coroutines.launch

/** How far a button shrinks under the finger; big surfaces (cards, tiles) use a gentler [PRESSED_SCALE_LARGE]. */
const val PRESSED_SCALE = 0.94f
const val PRESSED_SCALE_LARGE = 0.97f

private const val PRESS_DOWN_MILLISECONDS = 80
private val releaseSpring = spring<Float>(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow)

/**
 * Clickable without a ripple that dips under the finger and bounces back on release.
 * Follows the finger itself rather than the press interaction, which scrollable parents (lists,
 * sheets) only report after a delay: a quick tap there would otherwise not move at all.
 * Put it first in the chain so the button's background and frame scale with it.
 */
@Composable
fun Modifier.pressable(
    enabled: Boolean = true,
    pressedScale: Float = PRESSED_SCALE,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
): Modifier {
    val scale = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()
    val interactionSource = remember { MutableInteractionSource() }
    var clickHandling = Modifier.clickable(interactionSource = interactionSource, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
    if (onLongClick != null) {
        clickHandling = Modifier.combinedClickable(
            interactionSource = interactionSource,
            indication = null,
            enabled = enabled,
            role = Role.Button,
            onLongClick = onLongClick,
            onClick = onClick,
        )
    }
    return this
        .graphicsLayer {
            scaleX = scale.value
            scaleY = scale.value
        }
        .pointerInput(enabled, pressedScale) {
            if (!enabled) {
                return@pointerInput
            }
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                scope.launch { scale.animateTo(pressedScale, tween(PRESS_DOWN_MILLISECONDS)) }
                waitForUpOrCancellation()
                scope.launch { springBack(scale, pressedScale) }
            }
        }
        .then(clickHandling)
}

// A tap shorter than the dip still shows it whole before bouncing back.
private suspend fun springBack(scale: Animatable<Float, *>, pressedScale: Float) {
    if (scale.value > pressedScale) {
        scale.animateTo(pressedScale, tween(PRESS_DOWN_MILLISECONDS))
    }
    scale.animateTo(1f, releaseSpring)
}
