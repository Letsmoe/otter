package dev.neoworks.otter.ui.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Fill
import com.adamglin.phosphoricons.Regular
import com.adamglin.phosphoricons.fill.Play
import com.adamglin.phosphoricons.regular.X

private const val NEXT_COUNTDOWN_MILLISECONDS = 8000

/**
 * Shown when playback ends: title and rating, close, and for episodes a "Next Episode" button
 * that fills up and starts on its own. Mirrors EndScreen.svelte.
 */
@Composable
fun EndScreen(title: String, mediaId: String, hasNext: Boolean, onNext: () -> Unit, onClose: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.7f)).windowInsetsPadding(WindowInsets.safeDrawing)) {
        GlassIconButton(PhosphorIcons.Regular.X, "Close player", 48.dp, onClose, Modifier.align(Alignment.TopStart).padding(start = 20.dp, top = 14.dp))
        Column(Modifier.align(Alignment.BottomStart).padding(start = 24.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, fontSize = 24.sp, fontWeight = FontWeight.Black)
            RatingTrio(mediaId)
        }
        if (hasNext) {
            Row(Modifier.align(Alignment.BottomEnd).padding(end = 24.dp, bottom = 24.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextPill("Watch Credits", onClose)
                NextEpisodePill(onNext)
            }
        }
    }
}

@Composable
private fun TextPill(label: String, onClick: () -> Unit) {
    Box(Modifier.height(44.dp).playerGlass(CircleShape).clickableWithoutRipple(onClick = onClick).padding(horizontal = 20.dp), contentAlignment = Alignment.Center) {
        Text(label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}

// Fills left to right over the countdown, like Netflix's autoplay bar.
@Composable
private fun NextEpisodePill(onNext: () -> Unit) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        progress.animateTo(1f, tween(NEXT_COUNTDOWN_MILLISECONDS, easing = LinearEasing))
        onNext()
    }
    Box(Modifier.height(44.dp).playerGlass(CircleShape).clickableWithoutRipple(onClick = onNext), contentAlignment = Alignment.Center) {
        Box(Modifier.matchParentSize()) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(progress.value).background(Color.White))
        }
        PillLabel(Color.White, Modifier)
        // Over the filled part the label turns dark.
        PillLabel(Color.Black, Modifier.clip(LeadingFractionShape(progress.value)))
    }
}

private class LeadingFractionShape(private val fraction: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        return Outline.Rectangle(Rect(0f, 0f, size.width * fraction, size.height))
    }
}

@Composable
private fun PillLabel(color: Color, modifier: Modifier) {
    Row(modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(PhosphorIcons.Fill.Play, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
        Text("Next Episode", color = color, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}
