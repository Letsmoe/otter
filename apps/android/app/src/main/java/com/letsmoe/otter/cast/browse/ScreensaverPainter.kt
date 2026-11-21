package com.letsmoe.otter.cast.browse

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.TextPaint
import android.text.TextUtils

// Sizes are in pixels of a 1080-line picture and scale with the real one.
private const val REFERENCE_HEIGHT = 1080f
private const val SIDE_MARGIN = 96f
private const val BOTTOM_MARGIN = 84f
private const val LABEL_TITLE_SIZE = 42f
private const val LABEL_YEAR_SIZE = 28f
private const val LABEL_GAP = 6f
private const val LABEL_MAX_WIDTH = 900f
private const val SHADE_HEIGHT_FRACTION = 0.3f
private val BASE_COLOR = Color.rgb(11, 11, 13)
private val SHADE_COLOR = Color.argb(200, 8, 8, 10)
private val MUTED_WHITE = Color.argb(190, 255, 255, 255)

/** A backdrop with the slide it belongs to; the backdrop is null while it loads or when it failed. */
data class ScreensaverLayer(val slide: ScreensaverSlide, val backdrop: Bitmap?)

/**
 * The idle TV's picture: [current]'s backdrop full screen with its title small in the bottom left
 * corner, and while [fade] is above 0 the [next] one fading in over it, title included.
 */
fun drawScreensaverFrame(canvas: Canvas, current: ScreensaverLayer?, next: ScreensaverLayer?, fade: Float) {
    canvas.drawColor(BASE_COLOR)
    if (current != null) {
        drawBackdrop(canvas, current, 1f)
    }
    if (next != null && fade > 0f) {
        drawBackdrop(canvas, next, fade)
    }
    drawShade(canvas)
    drawLabels(canvas, current, next, fade)
}

private fun drawBackdrop(canvas: Canvas, layer: ScreensaverLayer, opacity: Float) {
    val backdrop = layer.backdrop ?: return
    val slot = RectF(0f, 0f, canvas.width.toFloat(), canvas.height.toFloat())
    val paint = Paint(Paint.FILTER_BITMAP_FLAG)
    paint.alpha = alphaOf(opacity)
    canvas.drawBitmap(backdrop, centerCrop(backdrop, slot), slot, paint)
}

private fun alphaOf(opacity: Float): Int = (opacity * 255).toInt().coerceIn(0, 255)

private fun drawShade(canvas: Canvas) {
    val height = canvas.height.toFloat()
    val top = height * (1f - SHADE_HEIGHT_FRACTION)
    val shade = Paint()
    shade.shader = LinearGradient(0f, top, 0f, height, Color.TRANSPARENT, SHADE_COLOR, Shader.TileMode.CLAMP)
    canvas.drawRect(0f, top, canvas.width.toFloat(), height, shade)
}

// The old title fades out during the first half of the crossfade, the new one in during the second.
private fun drawLabels(canvas: Canvas, current: ScreensaverLayer?, next: ScreensaverLayer?, fade: Float) {
    if (current != null) {
        drawLabel(canvas, current.slide, alphaOf(1f - 2f * fade))
    }
    if (next != null && fade > 0.5f) {
        drawLabel(canvas, next.slide, alphaOf(2f * fade - 1f))
    }
}

private fun drawLabel(canvas: Canvas, slide: ScreensaverSlide, alpha: Int) {
    val scale = canvas.height / REFERENCE_HEIGHT
    val title = textPaint(LABEL_TITLE_SIZE * scale, Color.WHITE, Typeface.BOLD)
    title.alpha = alpha
    var baseline = canvas.height - BOTTOM_MARGIN * scale - title.descent()
    val left = SIDE_MARGIN * scale
    if (slide.year != null) {
        val year = textPaint(LABEL_YEAR_SIZE * scale, MUTED_WHITE, Typeface.NORMAL)
        year.alpha = alpha * Color.alpha(MUTED_WHITE) / 255
        canvas.drawText(slide.year.toString(), left, baseline, year)
        baseline -= year.fontSpacing + LABEL_GAP * scale
    }
    canvas.drawText(ellipsized(slide.title, title, LABEL_MAX_WIDTH * scale), left, baseline, title)
}

private fun ellipsized(text: String, paint: TextPaint, maxWidth: Float): String {
    return TextUtils.ellipsize(text, paint, maxWidth, TextUtils.TruncateAt.END).toString()
}
