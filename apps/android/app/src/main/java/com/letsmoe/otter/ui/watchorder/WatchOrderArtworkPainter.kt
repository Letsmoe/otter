package com.letsmoe.otter.ui.watchorder

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import androidx.compose.ui.graphics.toArgb
import com.letsmoe.otter.ui.theme.OtterColors
import java.util.Locale

/** The images the artwork is made of: the franchise's backdrop and the films' posters by part index. */
class ArtworkImages(val backdrop: Bitmap?, val posters: Map<Int, Bitmap>)

private val accentColor = OtterColors.accent.toArgb()
private val watchedColor = OtterColors.success.toArgb()
private val panelColor = OtterColors.secondary.toArgb()

private const val POSTER_CORNER = 12f
private const val HEADLINE_LEFT = ARTWORK_MARGIN
private const val LABEL_BASELINE = 96f
private const val HEADLINE_TOP = 124f
private const val SUBLINE_SIZE = 34f
private const val SUBLINE_SPACE = 70f

/**
 * Draws a franchise's watch-order artwork: its backdrop darkened under strong typography, and
 * the films' posters in release order on a numbered timeline that shows the profile's progress.
 * Made on the phone from the franchise's own images; the same drawing serves the guide's header
 * and the Franchises row. [draw] scales the 1600x900 design to the canvas width.
 */
class WatchOrderArtworkPainter {

    private val headlineTypeface = Typeface.create("sans-serif-black", Typeface.NORMAL)
    private val boldTypeface = Typeface.create("sans-serif", Typeface.BOLD)
    private val regularTypeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)

    fun draw(canvas: Canvas, guide: WatchOrderGuide, images: ArtworkImages, width: Float) {
        val layout = layoutArtwork(guide.parts.size)
        canvas.save()
        canvas.scale(width / ARTWORK_WIDTH, width / ARTWORK_WIDTH)
        drawBackground(canvas, guide, images.backdrop)
        drawHeadline(canvas, guide, layout)
        for (row in layout.rows) {
            drawTimeline(canvas, row, guide)
        }
        for (slot in layout.slots) {
            drawSlot(canvas, slot, guide, images, layout.hiddenCount)
        }
        canvas.restore()
    }

    private fun drawBackground(canvas: Canvas, guide: WatchOrderGuide, backdrop: Bitmap?) {
        val full = RectF(0f, 0f, ARTWORK_WIDTH, ARTWORK_HEIGHT)
        if (backdrop == null) {
            canvas.drawRect(full, Paint().apply { color = fallbackBackground(guide.name) })
        } else {
            drawBitmapCover(canvas, backdrop, full, Paint(Paint.FILTER_BITMAP_FLAG))
        }
        val fromLeft = LinearGradient(0f, 0f, 1200f, 0f, Color.argb(235, 0, 0, 0), Color.argb(0, 0, 0, 0), Shader.TileMode.CLAMP)
        canvas.drawRect(full, Paint().apply { shader = fromLeft })
        val fromBottom = LinearGradient(0f, 250f, 0f, ARTWORK_HEIGHT, Color.argb(0, 0, 0, 0), Color.argb(245, 0, 0, 0), Shader.TileMode.CLAMP)
        canvas.drawRect(full, Paint().apply { shader = fromBottom })
    }

    private fun fallbackBackground(name: String): Int {
        val hue = (name.hashCode().toLong() and 0x7fffffffL) % 360
        return Color.HSVToColor(floatArrayOf(hue.toFloat(), 0.45f, 0.28f))
    }

    private fun drawHeadline(canvas: Canvas, guide: WatchOrderGuide, layout: ArtworkLayout) {
        val label = textPaint(boldTypeface, 30f, accentColor).apply { letterSpacing = 0.3f }
        canvas.drawText("WATCH ORDER", HEADLINE_LEFT, LABEL_BASELINE, label)
        val headlinePaint = textPaint(headlineTypeface, 100f, Color.WHITE).apply {
            letterSpacing = -0.01f
            setShadowLayer(24f, 0f, 6f, Color.argb(170, 0, 0, 0))
        }
        val headline = guide.headline.uppercase(Locale.getDefault())
        val maxHeight = layout.titleBottom - HEADLINE_TOP - SUBLINE_SPACE
        val fit = fitHeadline(headline, ARTWORK_WIDTH - 2 * ARTWORK_MARGIN, maxHeight) { text, size ->
            headlinePaint.textSize = size
            headlinePaint.measureText(text)
        }
        headlinePaint.textSize = fit.fontSize
        val lineHeight = fit.fontSize * HEADLINE_LINE_HEIGHT
        fit.lines.forEachIndexed { index, line ->
            canvas.drawText(line, HEADLINE_LEFT, HEADLINE_TOP + fit.fontSize * 0.8f + index * lineHeight, headlinePaint)
        }
        val subBaseline = HEADLINE_TOP + fit.lines.size * lineHeight + SUBLINE_SIZE + 14f
        drawSubline(canvas, guide, subBaseline)
    }

    private fun drawSubline(canvas: Canvas, guide: WatchOrderGuide, baseline: Float) {
        val paint = textPaint(regularTypeface, SUBLINE_SIZE, Color.argb(220, 255, 255, 255)).apply { letterSpacing = 0.08f }
        val facts = mutableListOf("${guide.parts.size} FILMS")
        guide.yearSpan?.let { span -> facts.add(span) }
        val text = facts.joinToString("  ·  ")
        canvas.drawText(text, HEADLINE_LEFT, baseline, paint)
        if (!guide.isStarted) {
            return
        }
        val progress = textPaint(boldTypeface, SUBLINE_SIZE, watchedColor).apply { letterSpacing = 0.08f }
        val progressLeft = HEADLINE_LEFT + paint.measureText("$text  ·  ")
        canvas.drawText("${guide.watchedCount} OF ${guide.parts.size} WATCHED", progressLeft, baseline, progress)
    }

    private fun drawTimeline(canvas: Canvas, row: TimelineRow, guide: WatchOrderGuide) {
        val slots = row.slots
        if (slots.size < 2) {
            return
        }
        val base = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(80, 255, 255, 255)
            strokeWidth = 4f
        }
        canvas.drawLine(slots.first().circleX, row.y, slots.last().circleX, row.y, base)
        val done = Paint(base).apply { color = watchedColor }
        for (index in 0 until slots.size - 1) {
            val part = guide.parts.getOrNull(slots[index].partIndex)
            if (part != null && part.status == PartStatus.WATCHED) {
                canvas.drawLine(slots[index].circleX, row.y, slots[index + 1].circleX, row.y, done)
            }
        }
    }

    private fun drawSlot(canvas: Canvas, slot: PosterSlot, guide: WatchOrderGuide, images: ArtworkImages, hiddenCount: Int) {
        if (slot.partIndex == OVERFLOW_SLOT) {
            drawOverflowTile(canvas, slot, hiddenCount + 1)
            return
        }
        val part = guide.parts[slot.partIndex]
        val isNext = part.number == guide.nextNumber
        drawPoster(canvas, slot.poster, part, isNext, images.posters[slot.partIndex])
        drawCircle(canvas, slot, part, isNext)
        drawYear(canvas, slot, part)
    }

    private fun drawPoster(canvas: Canvas, box: Box, part: GuidePart, isNext: Boolean, poster: Bitmap?) {
        val rect = RectF(box.left, box.top, box.right, box.bottom)
        canvas.save()
        canvas.clipPath(Path().apply { addRoundRect(rect, POSTER_CORNER, POSTER_CORNER, Path.Direction.CW) })
        if (poster == null) {
            drawMissingPoster(canvas, rect, part)
        } else {
            drawBitmapCover(canvas, poster, rect, Paint(Paint.FILTER_BITMAP_FLAG))
        }
        dimUnavailable(canvas, rect, part)
        drawProgressBar(canvas, rect, part)
        canvas.restore()
        drawPosterFrame(canvas, rect, isNext)
    }

    private fun drawMissingPoster(canvas: Canvas, rect: RectF, part: GuidePart) {
        canvas.drawRect(rect, Paint().apply { color = panelColor })
        val paint = textPaint(boldTypeface, rect.width() * 0.5f, Color.argb(70, 255, 255, 255))
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText(part.number.toString(), rect.centerX(), rect.centerY() + rect.width() * 0.18f, paint)
    }

    private fun dimUnavailable(canvas: Canvas, rect: RectF, part: GuidePart) {
        var alpha = 0
        if (part.status == PartStatus.WATCHED) {
            alpha = 120
        }
        if (!part.released) {
            alpha = 150
        }
        if (alpha > 0) {
            canvas.drawRect(rect, Paint().apply { color = Color.argb(alpha, 0, 0, 0) })
        }
    }

    private fun drawProgressBar(canvas: Canvas, rect: RectF, part: GuidePart) {
        if (part.status != PartStatus.IN_PROGRESS) {
            return
        }
        val height = 9f
        val track = RectF(rect.left, rect.bottom - height, rect.right, rect.bottom)
        canvas.drawRect(track, Paint().apply { color = Color.argb(150, 0, 0, 0) })
        val filled = RectF(track.left, track.top, track.left + track.width() * part.progress, track.bottom)
        canvas.drawRect(filled, Paint().apply { color = accentColor })
    }

    private fun drawPosterFrame(canvas: Canvas, rect: RectF, isNext: Boolean) {
        val frame = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2f
            color = Color.argb(60, 255, 255, 255)
        }
        if (isNext) {
            frame.strokeWidth = 6f
            frame.color = Color.WHITE
        }
        canvas.drawRoundRect(rect, POSTER_CORNER, POSTER_CORNER, frame)
    }

    private fun drawCircle(canvas: Canvas, slot: PosterSlot, part: GuidePart, isNext: Boolean) {
        val fill = Paint(Paint.ANTI_ALIAS_FLAG)
        val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 3f
            color = Color.argb(150, 255, 255, 255)
        }
        var numberColor = Color.WHITE
        fill.color = Color.argb(220, 12, 12, 14)
        if (part.status == PartStatus.WATCHED) {
            fill.color = watchedColor
            numberColor = Color.argb(255, 8, 40, 20)
        }
        if (part.status == PartStatus.IN_PROGRESS) {
            fill.color = accentColor
            numberColor = Color.argb(255, 20, 10, 50)
            ring.color = Color.WHITE
        }
        if (isNext && part.status == PartStatus.NOT_YET) {
            fill.color = Color.WHITE
            numberColor = Color.BLACK
        }
        canvas.drawCircle(slot.circleX, slot.circleY, slot.circleRadius, fill)
        canvas.drawCircle(slot.circleX, slot.circleY, slot.circleRadius, ring)
        if (part.status == PartStatus.WATCHED) {
            drawCheck(canvas, slot, numberColor)
            return
        }
        drawNumber(canvas, slot, part.number.toString(), numberColor)
    }

    private fun drawNumber(canvas: Canvas, slot: PosterSlot, text: String, color: Int) {
        val paint = textPaint(boldTypeface, slot.circleRadius * 1.05f, color).apply { textAlign = Paint.Align.CENTER }
        canvas.drawText(text, slot.circleX, slot.circleY + paint.textSize * 0.35f, paint)
    }

    private fun drawCheck(canvas: Canvas, slot: PosterSlot, color: Int) {
        val radius = slot.circleRadius
        val path = Path().apply {
            moveTo(slot.circleX - radius * 0.42f, slot.circleY + radius * 0.02f)
            lineTo(slot.circleX - radius * 0.1f, slot.circleY + radius * 0.34f)
            lineTo(slot.circleX + radius * 0.46f, slot.circleY - radius * 0.34f)
        }
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = radius * 0.2f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            this.color = color
        }
        canvas.drawPath(path, stroke)
    }

    private fun drawYear(canvas: Canvas, slot: PosterSlot, part: GuidePart) {
        var caption = "TBA"
        if (part.year != null) {
            caption = part.year.toString()
        }
        var alpha = 210
        if (part.status == PartStatus.WATCHED) {
            alpha = 120
        }
        val paint = textPaint(regularTypeface, slot.captionSize, Color.argb(alpha, 255, 255, 255)).apply {
            textAlign = Paint.Align.CENTER
            letterSpacing = 0.05f
        }
        canvas.drawText(caption, slot.circleX, slot.captionBaseline, paint)
    }

    private fun drawOverflowTile(canvas: Canvas, slot: PosterSlot, count: Int) {
        val box = slot.poster
        val rect = RectF(box.left, box.top, box.right, box.bottom)
        canvas.drawRoundRect(rect, POSTER_CORNER, POSTER_CORNER, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = panelColor })
        val paint = textPaint(headlineTypeface, box.width * 0.34f, Color.WHITE).apply { textAlign = Paint.Align.CENTER }
        canvas.drawText("+$count", box.centerX, rect.centerY() + paint.textSize * 0.35f, paint)
        val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 3f
            color = Color.argb(150, 255, 255, 255)
        }
        canvas.drawCircle(slot.circleX, slot.circleY, slot.circleRadius, ring)
    }

    private fun textPaint(typeface: Typeface, size: Float, color: Int): Paint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.typeface = typeface
            textSize = size
            this.color = color
        }

    private fun drawBitmapCover(canvas: Canvas, bitmap: Bitmap, target: RectF, paint: Paint) {
        val crop = coverCrop(bitmap.width, bitmap.height, target.width() / target.height())
        canvas.drawBitmap(bitmap, Rect(crop.left, crop.top, crop.right, crop.bottom), target, paint)
    }
}
