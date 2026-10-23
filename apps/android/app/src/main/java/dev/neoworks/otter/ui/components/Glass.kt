package dev.neoworks.otter.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.HazeColorEffect
import dev.chrisbanes.haze.blur.hazeBlur
import dev.chrisbanes.haze.glass.GlassStyle
import dev.chrisbanes.haze.glass.hazeGlass
import dev.neoworks.otter.ui.theme.OtterColors

/** What glass surfaces blur: the content marked with `hazeSource` under the nearest provider. */
val LocalGlassBackdrop = staticCompositionLocalOf<HazeState?> { null }

// Plain translucent fill for glass drawn without a backdrop (e.g. nested sheets).
val GlassColor = Color(0xCC141416)
val SubtleFill = Color.White.copy(alpha = 0.06f)

private const val FROSTED_SATURATION = 1.8f
private val frostedBlurRadius = 20.dp

// Web `glass-bar`: base-200 at 65% over a 20px blur, saturated 1.8×.
private val frostedStyle = HazeBlurStyle {
    blurRadius(frostedBlurRadius)
    backgroundColor(OtterColors.base200)
    colorEffects(
        listOf(
            HazeColorEffect.colorFilter(ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(FROSTED_SATURATION) })),
            HazeColorEffect.tint(OtterColors.base200.copy(alpha = 0.65f)),
        ),
    )
    fallbackColorEffect(HazeColorEffect.tint(GlassColor))
}

// 10% hairline, a little brighter along the top edge like the web's inset highlight.
private val glassRimBrush = Brush.verticalGradient(
    0f to Color.White.copy(alpha = 0.16f),
    0.3f to Color.White.copy(alpha = 0.10f),
    1f to Color.White.copy(alpha = 0.08f),
)

private fun Modifier.glassShadow(shape: Shape): Modifier = dropShadow(
    shape = shape,
    shadow = Shadow(radius = 30.dp, color = Color.Black.copy(alpha = 0.45f), offset = DpOffset(0.dp, 10.dp)),
)

/** Frosted glass over [LocalGlassBackdrop], with the web app's rim and shadow. */
@Composable
fun Modifier.frostedGlass(shape: Shape, withRim: Boolean = true): Modifier {
    val backdrop = LocalGlassBackdrop.current
    var styled = glassShadow(shape).clip(shape)
    if (backdrop != null) {
        styled = styled.hazeBlur(input = HazeInput.Sources(backdrop), style = frostedStyle)
    } else {
        styled = styled.then(Modifier.background(GlassColor))
    }
    if (withRim) {
        styled = styled.border(1.dp, glassRimBrush, shape)
    }
    return styled
}

/** Liquid glass (refraction, specular rim) over [LocalGlassBackdrop]; frosted where it can't render. */
@OptIn(ExperimentalHazeApi::class)
@Composable
fun Modifier.liquidGlass(cornerRadius: Dp): Modifier {
    val shape = RoundedCornerShape(cornerRadius)
    val backdrop = LocalGlassBackdrop.current ?: return frostedGlass(shape)
    // Haze's defaults suit light UIs (bright fill and tint); over Otter's black they read as flat grey.
    val style = GlassStyle {
        shape(shape)
        backgroundColor(OtterColors.base200)
        tint(OtterColors.base200.copy(alpha = 0.65f))
        ambientResponse(0f)
        whitePoint(0f)
    }
    return glassShadow(shape)
        .hazeGlass(input = HazeInput.Sources(backdrop), style = style)
        .border(1.dp, glassRimBrush, shape)
}
