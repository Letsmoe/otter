package dev.neoworks.otter.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp

// Richer colour than the content itself, so the glow reads as light rather than grey haze.
private const val GLOW_SATURATION = 1.7f
private val glowColorFilter = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(GLOW_SATURATION) })

/**
 * Draws a hard-blurred, dimmed copy of the content behind the content itself, so posters glow
 * into the page around them like a lit cinema screen instead of sitting on flat black.
 * The content is recorded once and drawn twice.
 */
@Composable
fun Modifier.blurredCopyBehind(radius: Dp, dim: Float): Modifier {
    val contentLayer = rememberGraphicsLayer()
    val blurredLayer = rememberGraphicsLayer()
    val radiusPixels = with(LocalDensity.current) { radius.toPx() }
    // Clipped: the blur spreads past the content and would bleed into the neighbouring tab page.
    return clipToBounds().drawWithContent {
        contentLayer.record { this@drawWithContent.drawContent() }
        blurredLayer.renderEffect = BlurEffect(radiusPixels, radiusPixels, TileMode.Clamp)
        blurredLayer.colorFilter = glowColorFilter
        blurredLayer.record { drawLayer(contentLayer) }
        drawLayer(blurredLayer)
        drawRect(Color.Black.copy(alpha = dim))
        drawLayer(contentLayer)
    }
}
