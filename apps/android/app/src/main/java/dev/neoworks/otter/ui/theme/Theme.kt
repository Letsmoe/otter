package dev.neoworks.otter.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Surface ramp and accents from packages/otter-app/src/theme.css.
object OtterColors {
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
}

private val otterColorScheme = darkColorScheme(
    primary = OtterColors.baseContent,
    onPrimary = OtterColors.base200,
    secondary = OtterColors.secondary,
    onSecondary = OtterColors.secondaryContent,
    tertiary = OtterColors.accent,
    onTertiary = OtterColors.base100,
    background = OtterColors.base100,
    onBackground = OtterColors.baseContent,
    surface = OtterColors.base200,
    onSurface = OtterColors.baseContent,
    surfaceVariant = OtterColors.base300,
    onSurfaceVariant = OtterColors.secondaryContent,
    outline = OtterColors.neutralContent,
    error = OtterColors.error,
    onError = OtterColors.base100,
)

@Composable
fun OtterTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = otterColorScheme, content = content)
}
