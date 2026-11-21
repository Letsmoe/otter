package com.letsmoe.otter.ui.home

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade

// How far below the featured area the glow keeps fading, so it runs out under the first row.
private val fadeBelow = 160.dp
private val glowBlur = 72.dp
private const val GLOW_ALPHA = 0.75f

// Keeps white text and the translucent My List button readable on light posters.
private val glowShade = Color.Black.copy(alpha = 0.35f)

/**
 * Background of the featured area: the poster stretched across the screen and blurred, so it reads
 * as the poster spreading out, over its [ambientColor]. Reaches up [reachAbove] behind the header
 * and fades into the page below. Before Android 12 (no blur) only the colour shows.
 */
@Composable
fun PosterGlow(posterUrl: String?, ambientColor: Color, reachAbove: Dp, modifier: Modifier = Modifier) {
    Box(
        modifier
            .extendVertically(reachAbove, fadeBelow)
            .fadeToTransparent()
            .background(ambientColor),
    ) {
        if (posterUrl != null && blurSupported()) {
            BlurredPoster(posterUrl)
        }
    }
}

@Composable
private fun BlurredPoster(posterUrl: String) {
    val request = ImageRequest.Builder(LocalContext.current).data(posterUrl).crossfade(true).build()
    AsyncImage(
        model = request,
        contentDescription = null,
        contentScale = ContentScale.FillBounds,
        alpha = GLOW_ALPHA,
        colorFilter = ColorFilter.tint(glowShade, BlendMode.SrcAtop),
        modifier = Modifier.fillMaxSize().blur(glowBlur),
    )
}

private fun blurSupported(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

/** Lays the content out [above] taller at the top and [below] taller at the bottom than its slot. */
private fun Modifier.extendVertically(above: Dp, below: Dp): Modifier = layout { measurable, constraints ->
    val abovePx = above.roundToPx()
    val height = constraints.maxHeight + abovePx + below.roundToPx()
    val placeable = measurable.measure(Constraints.fixed(constraints.maxWidth, height))
    layout(constraints.maxWidth, constraints.maxHeight) {
        placeable.place(0, -abovePx)
    }
}

/** Fades everything drawn after it out towards the bottom. */
private fun Modifier.fadeToTransparent(): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        val mask = Brush.verticalGradient(
            0f to Color.Black,
            0.55f to Color.Black,
            0.8f to Color.Black.copy(alpha = 0.45f),
            1f to Color.Transparent,
        )
        drawRect(mask, blendMode = BlendMode.DstIn)
    }
