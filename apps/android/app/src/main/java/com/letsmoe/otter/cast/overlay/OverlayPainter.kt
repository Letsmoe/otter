package com.letsmoe.otter.cast.overlay

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import androidx.media3.common.util.Size
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BitmapOverlay

// Sizes are in pixels of a 1080-line picture and scale with the real one.
private const val REFERENCE_HEIGHT = 1080f
private const val CAPTION_TEXT_SIZE = 50f
private const val CAPTION_OUTLINE_WIDTH = 7f
private const val CAPTION_MAX_WIDTH_FRACTION = 0.62f
private const val BOTTOM_MARGIN = 72f
private const val SIDE_MARGIN = 64f
private const val HINT_TEXT_SIZE = 30f
private const val HINT_PADDING_HORIZONTAL = 34f
private const val HINT_PADDING_VERTICAL = 20f
private const val CARD_WIDTH = 420f
private const val CARD_RADIUS = 22f
private const val CARD_PADDING = 22f
private const val CARD_TITLE_SIZE = 32f
private const val CARD_LABEL_SIZE = 26f
private const val CARD_LINE_GAP = 8f
private val CARD_FILL = Color.argb(222, 18, 18, 22)
private val LABEL_COLOR = Color.argb(184, 255, 255, 255)

/**
 * Draws captions, the skip hint and the next episode's card into the frames
 * of one overlay stream. The stream starts at [startSeconds] of the title;
 * its first frame, whatever its timestamp, is that moment. The bitmap is only
 * redrawn (and uploaded to the GPU) when what it shows changes.
 */
@UnstableApi
class OverlayPainter(private val plan: OverlayPlan, private val startSeconds: Double) : BitmapOverlay() {

    private var canvasBitmap: Bitmap? = null
    private var drawnFrame: OverlayFrame? = null
    private var firstPresentationTimeUs: Long? = null

    override fun configure(videoSize: Size) {
        super.configure(videoSize)
        canvasBitmap = Bitmap.createBitmap(videoSize.width, videoSize.height, Bitmap.Config.ARGB_8888)
        drawnFrame = null
    }

    override fun getBitmap(presentationTimeUs: Long): Bitmap {
        val bitmap = checkNotNull(canvasBitmap) { "configure() comes first" }
        val frame = plan.frameAt(titleSecondsAt(presentationTimeUs))
        if (frame != drawnFrame) {
            drawFrame(Canvas(bitmap), frame, bitmap.height / REFERENCE_HEIGHT, plan.upNext)
            drawnFrame = frame
        }
        return bitmap
    }

    private fun titleSecondsAt(presentationTimeUs: Long): Double {
        val first = firstPresentationTimeUs ?: presentationTimeUs.also { firstPresentationTimeUs = it }
        return startSeconds + (presentationTimeUs - first) / 1_000_000.0
    }
}

/** Draws [frame] over a transparent picture; [scale] is the picture's height over 1080. */
fun drawFrame(canvas: Canvas, frame: OverlayFrame, scale: Float, upNext: UpNextHint?) {
    canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
    frame.caption?.let { caption -> drawCaption(canvas, caption, scale) }
    val secondsLeft = frame.upNextSecondsLeft
    if (secondsLeft != null && upNext != null) {
        drawUpNextCard(canvas, upNext, secondsLeft, scale)
        return
    }
    frame.skipLabel?.let { label -> drawSkipPill(canvas, label, scale) }
}

// White text with a dark outline: readable on any picture without a box.
private fun drawCaption(canvas: Canvas, caption: String, scale: Float) {
    val paint = TextPaint(Paint.ANTI_ALIAS_FLAG)
    paint.textSize = CAPTION_TEXT_SIZE * scale
    paint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
    val width = (canvas.width * CAPTION_MAX_WIDTH_FRACTION).toInt()
    val layout = StaticLayout.Builder.obtain(caption, 0, caption.length, paint, width)
        .setAlignment(Layout.Alignment.ALIGN_CENTER)
        .build()
    canvas.save()
    canvas.translate((canvas.width - width) / 2f, canvas.height - BOTTOM_MARGIN * scale - layout.height)
    paint.style = Paint.Style.STROKE
    paint.strokeWidth = CAPTION_OUTLINE_WIDTH * scale
    paint.strokeJoin = Paint.Join.ROUND
    paint.color = Color.argb(230, 0, 0, 0)
    layout.draw(canvas)
    paint.style = Paint.Style.FILL
    paint.color = Color.WHITE
    layout.draw(canvas)
    canvas.restore()
}

// Looks like the app's filled pill buttons: white with dark text, bottom right.
private fun drawSkipPill(canvas: Canvas, label: String, scale: Float) {
    val paint = TextPaint(Paint.ANTI_ALIAS_FLAG)
    paint.textSize = HINT_TEXT_SIZE * scale
    paint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
    val textBounds = Rect()
    paint.getTextBounds(label, 0, label.length, textBounds)
    val width = paint.measureText(label) + 2 * HINT_PADDING_HORIZONTAL * scale
    val height = textBounds.height() + 2 * HINT_PADDING_VERTICAL * scale
    val right = canvas.width - SIDE_MARGIN * scale
    val bottom = canvas.height - BOTTOM_MARGIN * scale
    val pill = RectF(right - width, bottom - height, right, bottom)
    val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    fill.color = Color.WHITE
    canvas.drawRoundRect(pill, height / 2, height / 2, fill)
    paint.color = Color.argb(255, 18, 18, 22)
    val baseline = pill.centerY() - textBounds.exactCenterY()
    canvas.drawText(label, pill.left + HINT_PADDING_HORIZONTAL * scale, baseline, paint)
}

// The still on top, then "Next episode in Ns" and the episode, bottom right.
private fun drawUpNextCard(canvas: Canvas, upNext: UpNextHint, secondsLeft: Int, scale: Float) {
    val width = CARD_WIDTH * scale
    val padding = CARD_PADDING * scale
    val title = titlePaint(scale)
    val label = labelPaint(scale)
    val stillHeight = stillHeightOf(upNext.still, width)
    val textHeight = title.fontSpacing + CARD_LINE_GAP * scale + label.fontSpacing
    val height = stillHeight + textHeight + 2 * padding
    val right = canvas.width - SIDE_MARGIN * scale
    val bottom = canvas.height - BOTTOM_MARGIN * scale
    val card = RectF(right - width, bottom - height, right, bottom)
    val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    fill.color = CARD_FILL
    val radius = CARD_RADIUS * scale
    canvas.drawRoundRect(card, radius, radius, fill)
    upNext.still?.let { still -> drawStill(canvas, still, RectF(card.left, card.top, card.right, card.top + stillHeight), radius) }
    val textLeft = card.left + padding
    val titleBaseline = card.top + stillHeight + padding - title.ascent()
    canvas.drawText("Next episode in ${secondsLeft}s", textLeft, titleBaseline, title)
    val episode = TextUtils.ellipsize(upNext.episodeLabel, label, width - 2 * padding, TextUtils.TruncateAt.END).toString()
    canvas.drawText(episode, textLeft, titleBaseline + title.descent() + CARD_LINE_GAP * scale - label.ascent(), label)
}

private fun titlePaint(scale: Float): TextPaint {
    val paint = TextPaint(Paint.ANTI_ALIAS_FLAG)
    paint.textSize = CARD_TITLE_SIZE * scale
    paint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
    paint.color = Color.WHITE
    return paint
}

private fun labelPaint(scale: Float): TextPaint {
    val paint = TextPaint(Paint.ANTI_ALIAS_FLAG)
    paint.textSize = CARD_LABEL_SIZE * scale
    paint.color = LABEL_COLOR
    return paint
}

// A 16:9 slot across the card's width; none without a still.
private fun stillHeightOf(still: Bitmap?, width: Float): Float {
    if (still == null) {
        return 0f
    }
    return width * 9 / 16
}

// Cropped to fill the slot, with the card's rounded top corners.
private fun drawStill(canvas: Canvas, still: Bitmap, slot: RectF, radius: Float) {
    canvas.save()
    val clip = Path()
    clip.addRoundRect(slot, floatArrayOf(radius, radius, radius, radius, 0f, 0f, 0f, 0f), Path.Direction.CW)
    canvas.clipPath(clip)
    canvas.drawBitmap(still, centerCrop(still, slot), slot, Paint(Paint.FILTER_BITMAP_FLAG))
    canvas.restore()
}

private fun centerCrop(still: Bitmap, slot: RectF): Rect {
    val slotRatio = slot.width() / slot.height()
    val stillRatio = still.width.toFloat() / still.height
    if (stillRatio > slotRatio) {
        val croppedWidth = (still.height * slotRatio).toInt()
        val left = (still.width - croppedWidth) / 2
        return Rect(left, 0, left + croppedWidth, still.height)
    }
    val croppedHeight = (still.width / slotRatio).toInt()
    val top = (still.height - croppedHeight) / 2
    return Rect(0, top, still.width, top + croppedHeight)
}
