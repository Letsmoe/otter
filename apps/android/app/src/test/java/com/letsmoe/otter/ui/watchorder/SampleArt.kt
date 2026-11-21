package com.letsmoe.otter.ui.watchorder

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.graphics.Typeface

/**
 * Stand-ins for TMDB's posters and backdrops, drawn here so previews render on a PC without a
 * TMDB key or network: a gradient, a few shapes and the film's title set like a poster.
 */
object SampleArt {

    fun poster(title: String, hue: Float, width: Int = 342, height: Int = 513): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val top = Color.HSVToColor(floatArrayOf(hue, 0.55f, 0.75f))
        val bottom = Color.HSVToColor(floatArrayOf((hue + 30f) % 360f, 0.7f, 0.18f))
        val sky = LinearGradient(0f, 0f, 0f, height.toFloat(), top, bottom, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), Paint().apply { shader = sky })
        drawSun(canvas, width, height, hue)
        drawRidges(canvas, width, height, hue)
        drawTitle(canvas, title, width, height)
        return bitmap
    }

    fun backdrop(hue: Float, width: Int = 1280, height: Int = 720): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val top = Color.HSVToColor(floatArrayOf(hue, 0.5f, 0.85f))
        val bottom = Color.HSVToColor(floatArrayOf((hue + 40f) % 360f, 0.65f, 0.25f))
        val sky = LinearGradient(0f, 0f, 0f, height.toFloat(), top, bottom, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), Paint().apply { shader = sky })
        drawSun(canvas, width, height, hue)
        drawRidges(canvas, width, height, hue)
        return bitmap
    }

    private fun drawSun(canvas: Canvas, width: Int, height: Int, hue: Float) {
        val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.HSVToColor(110, floatArrayOf((hue + 20f) % 360f, 0.15f, 1f)) }
        canvas.drawCircle(width * 0.68f, height * 0.34f, width * 0.2f, glow)
        canvas.drawCircle(width * 0.68f, height * 0.34f, width * 0.1f, Paint(glow).apply { color = Color.argb(200, 255, 245, 220) })
    }

    private fun drawRidges(canvas: Canvas, width: Int, height: Int, hue: Float) {
        for (layer in 0 until 3) {
            val base = height * (0.52f + layer * 0.14f)
            val path = Path().apply {
                moveTo(0f, height.toFloat())
                lineTo(0f, base)
                var x = 0f
                while (x < width) {
                    lineTo(x + width * 0.09f, base - height * (0.05f + 0.04f * ((x / width * 7f + layer).toInt() % 3)))
                    lineTo(x + width * 0.18f, base + height * 0.02f)
                    x += width * 0.18f
                }
                lineTo(width.toFloat(), height.toFloat())
                close()
            }
            val shade = Color.HSVToColor(floatArrayOf(hue, 0.5f, 0.32f - layer * 0.09f))
            canvas.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = shade })
        }
    }

    private fun drawTitle(canvas: Canvas, title: String, width: Int, height: Int) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create("sans-serif-black", Typeface.NORMAL)
            textSize = width * 0.105f
            color = Color.WHITE
            textAlign = Paint.Align.CENTER
            letterSpacing = 0.04f
            setShadowLayer(6f, 0f, 2f, Color.argb(180, 0, 0, 0))
        }
        val lines = wrap(title.uppercase(), paint, width * 0.86f)
        val lineHeight = paint.textSize * 1.1f
        var baseline = height - 28f - (lines.size - 1) * lineHeight
        for (line in lines) {
            canvas.drawText(line, width / 2f, baseline, paint)
            baseline += lineHeight
        }
    }

    private fun wrap(text: String, paint: Paint, maxWidth: Float): List<String> {
        val lines = mutableListOf<String>()
        var current = ""
        for (word in text.split(" ")) {
            var candidate = word
            if (current.isNotEmpty()) {
                candidate = "$current $word"
            }
            if (paint.measureText(candidate) <= maxWidth || current.isEmpty()) {
                current = candidate
                continue
            }
            lines.add(current)
            current = word
        }
        lines.add(current)
        return lines
    }
}
