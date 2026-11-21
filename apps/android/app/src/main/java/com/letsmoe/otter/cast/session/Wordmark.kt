package com.letsmoe.otter.cast.session

import android.content.Context
import android.graphics.Canvas
import androidx.core.content.ContextCompat
import com.letsmoe.otter.R
import kotlin.math.roundToInt

// Width over height of otter_wordmark.xml's viewport (1343 × 316).
private const val WORDMARK_ASPECT_RATIO = 1343f / 316f

/** How wide Otter's wordmark is when drawn [height] pixels tall. */
fun wordmarkWidth(height: Float): Float = height * WORDMARK_ASPECT_RATIO

/** Draws Otter's white wordmark [height] pixels tall, its top left corner at [left], [top], [alpha] opaque (0–255). */
fun drawWordmark(canvas: Canvas, context: Context, left: Float, top: Float, height: Float, alpha: Int = 255) {
    val wordmark = checkNotNull(ContextCompat.getDrawable(context, R.drawable.otter_wordmark)) { "otter_wordmark ships with the app" }
    val right = left + wordmarkWidth(height)
    wordmark.setBounds(left.roundToInt(), top.roundToInt(), right.roundToInt(), (top + height).roundToInt())
    wordmark.alpha = alpha
    wordmark.draw(canvas)
}
