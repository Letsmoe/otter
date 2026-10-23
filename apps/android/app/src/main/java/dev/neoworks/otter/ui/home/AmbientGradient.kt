package dev.neoworks.otter.ui.home

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// How far below the featured card the tint keeps fading, so it runs out under the first row.
private val fadeBelow = 160.dp

/**
 * Paints the featured card's [color] from the top of the screen (reaching up [reachAbove] into the
 * header area) down past the card, where it fades into the page. Scrolls along with the card.
 */
fun Modifier.ambientGradient(color: Color, reachAbove: Dp): Modifier = drawBehind {
    val top = -reachAbove.toPx()
    val height = size.height + reachAbove.toPx() + fadeBelow.toPx()
    val brush = Brush.verticalGradient(
        0f to color,
        0.45f to color.copy(alpha = 0.85f),
        0.8f to color.copy(alpha = 0.35f),
        1f to Color.Transparent,
        startY = top,
        endY = top + height,
    )
    drawRect(brush, topLeft = Offset(0f, top), size = Size(size.width, height))
}
