package com.letsmoe.otter.ui.components

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
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.HazeColorEffect
import dev.chrisbanes.haze.blur.hazeBlur
import com.letsmoe.otter.ui.theme.OtterColors

/** What glass surfaces blur: the content marked with `hazeSource` under the nearest provider. */
val LocalGlassBackdrop = staticCompositionLocalOf<HazeState?> { null }

// Plain translucent fill for glass drawn without a backdrop (e.g. nested sheets).
val GlassColor = Color(0xCC141416)
val SubtleFill = Color.White.copy(alpha = 0.06f)

// Slightly muted: the web's 1.8× saturation made bright posters glow through on the phone.
private const val FROSTED_SATURATION = 0.9f
private val frostedBlurRadius = 40.dp
private const val FROSTED_TINT_ALPHA = 0.82f

// Base-200 at 82% over a wide blur: what's behind only shows as soft color.
private val frostedStyle = HazeBlurStyle {
    blurRadius(frostedBlurRadius)
    backgroundColor(OtterColors.base200)
    colorEffects(
        listOf(
            HazeColorEffect.colorFilter(ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(FROSTED_SATURATION) })),
            HazeColorEffect.tint(OtterColors.base200.copy(alpha = FROSTED_TINT_ALPHA)),
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

/**
 * The floating bars' glass. Haze's hazeGlass (refraction) drew only its tint, no blur, on ColorOS
 * (Android 16), so the bars use the same frosted blur as every other surface.
 */
@Composable
fun Modifier.liquidGlass(cornerRadius: Dp): Modifier {
    return frostedGlass(RoundedCornerShape(cornerRadius))
}
