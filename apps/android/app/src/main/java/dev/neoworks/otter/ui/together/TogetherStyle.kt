package dev.neoworks.otter.ui.together

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.neoworks.otter.ui.components.SubtleFill

internal val sectionGap = 24.dp
internal val innerGap = 12.dp
internal val cardShape = RoundedCornerShape(20.dp)
internal val mutedText = Color.White.copy(alpha = 0.55f)
internal val faintText = Color.White.copy(alpha = 0.4f)

private const val PRESSED_SCALE = 0.96f

/** Clickable without a ripple that springs down to 96% while pressed. */
@Composable
internal fun Modifier.pressable(enabled: Boolean = true, onClick: () -> Unit): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    var targetScale = 1f
    if (pressed) {
        targetScale = PRESSED_SCALE
    }
    val scale by animateFloatAsState(targetScale, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium), label = "pressScale")
    return this
        .scale(scale)
        .clickable(interactionSource = interactionSource, indication = null, enabled = enabled, onClick = onClick)
}

internal fun Modifier.subtleCard(): Modifier {
    return this
        .background(SubtleFill, cardShape)
        .border(1.dp, Color.White.copy(alpha = 0.08f), cardShape)
}
