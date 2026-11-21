package com.letsmoe.otter.tv.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme

// Surface ramp and accents from packages/otter-app/src/theme.css, like the phone app's OtterColors.
object TvColors {
    val base100 = Color(0xFF000000)
    val base200 = Color(0xFF0B0B0D)
    val base300 = Color(0xFF141416)
    val baseContent = Color(0xFFFAFAFA)
    val secondary = Color(0xFF1C1C1F)
    val secondaryContent = Color(0xFFA1A1AA)
    val neutralContent = Color(0xFF71717A)
    val accent = Color(0xFFA78BFA)
    val success = Color(0xFF4ADE80)
    val warning = Color(0xFFFBBF24)
    val error = Color(0xFFF87171)
    val subtleFill = Color.White.copy(alpha = 0.08f)
}

/** TV-safe area: content stays clear of the screen edges that some TVs crop (5 % of a 960×540 dp TV screen). */
object TvSpacing {
    val screenHorizontal = 48.dp
    val screenVertical = 27.dp
}

/**
 * Text sizes for a 960×540 dp TV screen (every Android TV, whatever its pixels), read from
 * a sofa: a 14 sp body is about the smallest that stays readable at that distance.
 */
object TvType {
    val heroTitle = 30.sp
    val screenTitle = 24.sp
    val sectionTitle = 16.sp
    val rowTitle = 15.sp
    val body = 14.sp
    val detail = 12.sp
    val caption = 11.sp
}

private val otterColors = darkColorScheme(
    primary = TvColors.baseContent,
    onPrimary = TvColors.base200,
    secondary = TvColors.secondary,
    onSecondary = TvColors.secondaryContent,
    tertiary = TvColors.accent,
    onTertiary = TvColors.base100,
    background = TvColors.base100,
    onBackground = TvColors.baseContent,
    surface = TvColors.base200,
    onSurface = TvColors.baseContent,
    surfaceVariant = TvColors.base300,
    onSurfaceVariant = TvColors.secondaryContent,
    border = TvColors.baseContent,
    error = TvColors.error,
    onError = TvColors.base100,
)

@Composable
fun OtterTvTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = otterColors) {
        // Text outside a Surface would otherwise be black on the black background.
        CompositionLocalProvider(LocalContentColor provides TvColors.baseContent, content = content)
    }
}
