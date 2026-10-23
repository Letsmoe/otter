package dev.neoworks.otter.ui.home

import android.graphics.Bitmap
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.ColorUtils
import androidx.palette.graphics.Palette
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val neutralAmbient = Color(0xFF2A3036)

// Keeps the tint calm behind white text, whatever the poster's colours.
private const val MAX_SATURATION = 0.45f
private const val MAX_LIGHTNESS = 0.32f

/** A muted colour taken from [posterUrl], animated as the poster changes. */
@Composable
fun rememberAmbientColor(posterUrl: String?): Color {
    val context = LocalContext.current
    var target by remember { mutableStateOf(neutralAmbient) }
    LaunchedEffect(posterUrl) {
        if (posterUrl == null) {
            return@LaunchedEffect
        }
        val request = ImageRequest.Builder(context).data(posterUrl).allowHardware(false).build()
        val result = SingletonImageLoader.get(context).execute(request)
        if (result is SuccessResult) {
            target = withContext(Dispatchers.Default) { ambientColorOf(result.image.toBitmap()) }
        }
    }
    val animated by animateColorAsState(target, tween(600), label = "ambientColor")
    return animated
}

private fun ambientColorOf(bitmap: Bitmap): Color {
    val palette = Palette.from(bitmap).maximumColorCount(16).generate()
    val swatch = palette.darkMutedSwatch ?: palette.mutedSwatch ?: palette.dominantSwatch
    if (swatch == null) {
        return neutralAmbient
    }
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(swatch.rgb, hsl)
    hsl[1] = hsl[1].coerceAtMost(MAX_SATURATION)
    hsl[2] = hsl[2].coerceIn(0.18f, MAX_LIGHTNESS)
    return Color(ColorUtils.HSLToColor(hsl))
}

/** [color] mixed towards black by [amount], for surfaces that sit on the ambient tint. */
fun darkened(color: Color, amount: Float): Color = Color(ColorUtils.blendARGB(color.toArgb(), Color.Black.toArgb(), amount))
