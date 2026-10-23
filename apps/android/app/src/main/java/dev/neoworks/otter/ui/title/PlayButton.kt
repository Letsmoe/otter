package dev.neoworks.otter.ui.title

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Fill
import com.adamglin.phosphoricons.fill.Play
import dev.neoworks.otter.ui.components.frostedGlass

/**
 * The title page's glass Play button: a light sweep while sources load,
 * one pop when the first source is ready.
 */
@Composable
fun PlayButton(label: String, busy: Boolean, ready: Boolean, disabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val pop = remember { Animatable(1f) }
    LaunchedEffect(ready) {
        if (ready) {
            pop.snapTo(0.94f)
            pop.animateTo(1f, spring(dampingRatio = 0.4f, stiffness = Spring.StiffnessMedium))
        }
    }
    var contentAlpha = 1f
    if (disabled) {
        contentAlpha = 0.6f
    }
    Row(
        modifier
            .fillMaxWidth()
            .height(48.dp)
            .scale(pop.value)
            .alpha(contentAlpha)
            .frostedGlass(CircleShape)
            .lightSweep(busy)
            .clickable(enabled = !disabled, onClick = onClick),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(PhosphorIcons.Fill.Play, contentDescription = null, modifier = Modifier.size(22.dp))
        Text(label, fontWeight = FontWeight.SemiBold)
    }
}

/** A light band crossing the button while [active]. */
@Composable
private fun Modifier.lightSweep(active: Boolean): Modifier {
    if (!active) {
        return this
    }
    val transition = rememberInfiniteTransition(label = "playSweep")
    val progress by transition.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Restart),
        label = "playSweepProgress",
    )
    return drawWithContent {
        drawContent()
        val start = Offset(size.width * progress, 0f)
        val band = Brush.linearGradient(
            0.3f to Color.Transparent,
            0.5f to Color.White.copy(alpha = 0.18f),
            0.7f to Color.Transparent,
            start = start,
            end = Offset(start.x + size.width, 0f),
        )
        drawRect(band)
    }
}
