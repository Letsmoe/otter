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
import com.letsmoe.otter.graphql.type.SubtitleStyle
import com.letsmoe.otter.ui.player.CaptionColors
import com.letsmoe.otter.ui.player.CaptionLook

// Sizes are in pixels of a 1080-line picture and scale with the real one.
private const val REFERENCE_HEIGHT = 1080f
// Relative to the caption's text size.
private const val CAPTION_OUTLINE_FRACTION = 0.14f
private const val CAPTION_BOX_PADDING_FRACTION = 0.3f
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

/** What one bitmap shows: the moment's frame in the profile's caption look. */
private data class OverlayScene(val frame: OverlayFrame, val captionLook: CaptionLook)

/**
 * Draws captions, the skip hint and the next episode's card into the frames
 * of one overlay stream. The stream starts at the job's start second; its
 * first frame, whatever its timestamp, is that moment. The bitmap is only
 * redrawn (and uploaded to the GPU) when what it shows changes.
 */
@UnstableApi
class OverlayPainter(private val job: OverlayJob) : BitmapOverlay() {

    private var canvasBitmap: Bitmap? = null
    private var drawnScene: OverlayScene? = null
    private var firstPresentationTimeUs: Long? = null

    override fun configure(videoSize: Size) {
        super.configure(videoSize)
        canvasBitmap = Bitmap.createBitmap(videoSize.width, videoSize.height, Bitmap.Config.ARGB_8888)
        drawnScene = null
    }

    override fun getBitmap(presentationTimeUs: Long): Bitmap {
        val bitmap = checkNotNull(canvasBitmap) { "configure() comes first" }
        val scene = OverlayScene(visibleFrameAt(titleSecondsAt(presentationTimeUs)), job.captionLook())
        if (scene != drawnScene) {
            drawFrame(Canvas(bitmap), scene.frame, scene.captionLook, job.plan.upNext)
            drawnScene = scene
        }
        return bitmap
    }

    // A card the viewer cancelled on the phone leaves the picture too.
    private fun visibleFrameAt(seconds: Double): OverlayFrame {
        val frame = job.plan.frameAt(seconds)
        if (frame.upNextSecondsLeft == null || !job.upNextDismissed()) {
            return frame
        }
        return frame.copy(upNextSecondsLeft = null)
    }

    private fun titleSecondsAt(presentationTimeUs: Long): Double {
        val first = firstPresentationTimeUs ?: presentationTimeUs.also { firstPresentationTimeUs = it }
        return job.startSeconds + (presentationTimeUs - first) / 1_000_000.0
    }
}

/** Draws [frame] over a transparent picture, scaled to the picture's height. */
fun drawFrame(canvas: Canvas, frame: OverlayFrame, captionLook: CaptionLook, upNext: UpNextHint?) {
    val scale = canvas.height / REFERENCE_HEIGHT
    canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
    frame.caption?.let { caption -> drawCaption(canvas, caption, captionLook) }
    val secondsLeft = frame.upNextSecondsLeft
    if (secondsLeft != null && upNext != null) {
        drawUpNextCard(canvas, upNext, secondsLeft, scale)
        return
    }
    frame.skipLabel?.let { label -> drawSkipPill(canvas, label, scale) }
}

// The profile's size (as on the phone) and style, bottom center.
private fun drawCaption(canvas: Canvas, caption: String, captionLook: CaptionLook) {
    val paint = TextPaint(Paint.ANTI_ALIAS_FLAG)
    paint.textSize = captionLook.textFraction * canvas.height
    paint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
    val width = (canvas.width * CAPTION_MAX_WIDTH_FRACTION).toInt()
    val layout = StaticLayout.Builder.obtain(caption, 0, caption.length, paint, width)
        .setAlignment(Layout.Alignment.ALIGN_CENTER)
        .build()
    canvas.save()
    canvas.translate((canvas.width - width) / 2f, canvas.height - BOTTOM_MARGIN * canvas.height / REFERENCE_HEIGHT - layout.height)
    if (captionLook.style == SubtitleStyle.BOX) {
        drawBoxedCaption(canvas, layout, paint)
    } else {
        drawOutlinedCaption(canvas, layout, paint, captionTextColor(captionLook.style))
    }
    canvas.restore()
}

private fun captionTextColor(style: SubtitleStyle): Int {
    if (style == SubtitleStyle.YELLOW) {
        return CaptionColors.YELLOW
    }
    return Color.WHITE
}

private fun drawOutlinedCaption(canvas: Canvas, layout: StaticLayout, paint: TextPaint, textColor: Int) {
    paint.style = Paint.Style.STROKE
    paint.strokeWidth = paint.textSize * CAPTION_OUTLINE_FRACTION
    paint.strokeJoin = Paint.Join.ROUND
    paint.color = CaptionColors.OUTLINE
    layout.draw(canvas)
    paint.style = Paint.Style.FILL
    paint.color = textColor
    layout.draw(canvas)
}

// A dark box behind each line, as TV captions often look.
private fun drawBoxedCaption(canvas: Canvas, layout: StaticLayout, paint: TextPaint) {
    val box = Paint(Paint.ANTI_ALIAS_FLAG)
    box.color = CaptionColors.BOX
    val padding = paint.textSize * CAPTION_BOX_PADDING_FRACTION
    for (line in 0 until layout.lineCount) {
        val lineBox = RectF(
            layout.getLineLeft(line) - padding,
            layout.getLineTop(line).toFloat(),
            layout.getLineRight(line) + padding,
            layout.getLineBottom(line).toFloat(),
        )
        canvas.drawRect(lineBox, box)
    }
    paint.color = Color.WHITE
    layout.draw(canvas)
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
