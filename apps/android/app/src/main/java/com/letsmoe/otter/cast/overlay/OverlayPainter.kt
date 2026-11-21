package com.letsmoe.otter.cast.overlay

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
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
// The second language: its size relative to the main captions, and the gap between them.
private const val SECOND_CAPTION_SCALE = 0.8f
private const val SECOND_CAPTION_GAP_FRACTION = 0.25f
private const val TITLE_CARD_TOP = 64f
private const val TITLE_CARD_TITLE_SIZE = 58f
private const val TITLE_CARD_DETAIL_SIZE = 32f
private const val TITLE_CARD_MAX_WIDTH_FRACTION = 0.6f
private const val TITLE_SHADE_HEIGHT = 300f
private const val CHAT_TEXT_SIZE = 28f
private const val CHAT_MAX_WIDTH_FRACTION = 0.35f
private const val REACTION_SIZE = 64f
private const val REACTION_NAME_SIZE = 22f
private const val REACTION_LANE_WIDTH = 96f
// Reactions rise between these heights (fractions of the picture, from the top).
private const val REACTION_START_FRACTION = 0.62f
private const val REACTION_END_FRACTION = 0.3f
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
private val CARD_PROGRESS = Color.argb(70, 255, 255, 255)
private val LABEL_COLOR = Color.argb(184, 255, 255, 255)

/** What one bitmap shows: the moment's frame in the profile's caption look, and the room's reactions and chat. */
private data class OverlayScene(val frame: OverlayFrame, val captionLook: CaptionLook, val together: TogetherMarks)

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
    private val togetherLayer = TogetherLayer()

    override fun configure(videoSize: Size) {
        super.configure(videoSize)
        canvasBitmap = Bitmap.createBitmap(videoSize.width, videoSize.height, Bitmap.Config.ARGB_8888)
        drawnScene = null
    }

    override fun getBitmap(presentationTimeUs: Long): Bitmap {
        val bitmap = checkNotNull(canvasBitmap) { "configure() comes first" }
        val seconds = titleSecondsAt(presentationTimeUs)
        val scene = OverlayScene(visibleFrameAt(seconds), job.captionLook(), togetherLayer.marksAt(seconds, job.together()))
        if (scene != drawnScene) {
            val canvas = Canvas(bitmap)
            drawFrame(canvas, scene.frame, scene.captionLook, job.plan.upNext)
            drawTogether(canvas, scene.together)
            drawnScene = scene
        }
        return bitmap
    }

    // A card the viewer cancelled on the phone leaves the picture too.
    private fun visibleFrameAt(seconds: Double): OverlayFrame {
        val frame = job.plan.frameAt(seconds, job.captionDelaySeconds())
        if (frame.upNextSecondsLeft == null || !job.upNextDismissed()) {
            return frame
        }
        return frame.copy(upNextSecondsLeft = null, upNextProgress = null)
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
    frame.titleCard?.let { card -> drawTitleCard(canvas, card, scale) }
    drawCaptions(canvas, CaptionLines(frame.caption, frame.secondCaption), captionLook)
    val secondsLeft = frame.upNextSecondsLeft
    if (secondsLeft != null && upNext != null) {
        drawUpNextCard(canvas, upNext, UpNextCountdown(secondsLeft, frame.upNextProgress ?: 0f), scale)
        return
    }
    frame.skipLabel?.let { label -> drawSkipPill(canvas, label, scale) }
}

/** The main captions and, smaller under them, the second language's. */
private data class CaptionLines(val main: String?, val second: String?)

/** One block of caption text, laid out and ready to draw in its colour. */
private class CaptionBlock(val layout: StaticLayout, val paint: TextPaint, val textColor: Int)

// The profile's size (as on the phone) and style, bottom center; the second language sits under the main one.
private fun drawCaptions(canvas: Canvas, lines: CaptionLines, captionLook: CaptionLook) {
    val mainSize = captionLook.textFraction * canvas.height
    var bottom = canvas.height - BOTTOM_MARGIN * canvas.height / REFERENCE_HEIGHT
    lines.second?.let { second ->
        val block = captionBlock(canvas, second, mainSize * SECOND_CAPTION_SCALE, secondCaptionColor(captionLook.style))
        drawCaptionBlock(canvas, block, bottom, captionLook.style)
        bottom -= block.layout.height + mainSize * SECOND_CAPTION_GAP_FRACTION
    }
    lines.main?.let { main ->
        val block = captionBlock(canvas, main, mainSize, mainCaptionColor(captionLook.style))
        drawCaptionBlock(canvas, block, bottom, captionLook.style)
    }
}

private fun captionBlock(canvas: Canvas, text: String, textSize: Float, textColor: Int): CaptionBlock {
    val paint = TextPaint(Paint.ANTI_ALIAS_FLAG)
    paint.textSize = textSize
    paint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
    val width = (canvas.width * CAPTION_MAX_WIDTH_FRACTION).toInt()
    val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
        .setAlignment(Layout.Alignment.ALIGN_CENTER)
        .build()
    return CaptionBlock(layout, paint, textColor)
}

// Draws the block centered, its last line ending at [bottom].
private fun drawCaptionBlock(canvas: Canvas, block: CaptionBlock, bottom: Float, style: SubtitleStyle) {
    canvas.save()
    canvas.translate((canvas.width - block.layout.width) / 2f, bottom - block.layout.height)
    if (style == SubtitleStyle.BOX) {
        drawBoxedCaption(canvas, block.layout, block.paint, block.textColor)
    } else {
        drawOutlinedCaption(canvas, block.layout, block.paint, block.textColor)
    }
    canvas.restore()
}

private fun mainCaptionColor(style: SubtitleStyle): Int {
    if (style == SubtitleStyle.YELLOW) {
        return CaptionColors.YELLOW
    }
    return Color.WHITE
}

// Set apart from the main captions: white under yellow ones, light grey under white ones.
private fun secondCaptionColor(style: SubtitleStyle): Int {
    if (style == SubtitleStyle.YELLOW) {
        return Color.WHITE
    }
    return CaptionColors.SECOND
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
private fun drawBoxedCaption(canvas: Canvas, layout: StaticLayout, paint: TextPaint, textColor: Int) {
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
    paint.color = textColor
    layout.draw(canvas)
}

// "Severance" large with "S1:E2 · Half Loop" under it, top left, over a shade that keeps it readable.
private fun drawTitleCard(canvas: Canvas, card: TitleCard, scale: Float) {
    val shade = Paint()
    val shadeHeight = TITLE_SHADE_HEIGHT * scale
    shade.shader = LinearGradient(0f, 0f, 0f, shadeHeight, CARD_FILL, Color.TRANSPARENT, Shader.TileMode.CLAMP)
    canvas.drawRect(0f, 0f, canvas.width.toFloat(), shadeHeight, shade)
    val title = titleCardPaint(TITLE_CARD_TITLE_SIZE * scale, Color.WHITE, Typeface.BOLD)
    val left = SIDE_MARGIN * scale
    val maxWidth = canvas.width * TITLE_CARD_MAX_WIDTH_FRACTION
    val titleBaseline = TITLE_CARD_TOP * scale - title.ascent()
    canvas.drawText(ellipsized(card.title, title, maxWidth), left, titleBaseline, title)
    val detailPaint = titleCardPaint(TITLE_CARD_DETAIL_SIZE * scale, LABEL_COLOR, Typeface.NORMAL)
    var baseline = titleBaseline + title.descent() + CARD_LINE_GAP * scale - detailPaint.ascent()
    for (line in listOfNotNull(card.detail, card.endsAtLabel)) {
        canvas.drawText(ellipsized(line, detailPaint, maxWidth), left, baseline, detailPaint)
        baseline += detailPaint.fontSpacing
    }
}

/** The Watch Together room: chat top right, reactions rising up the right edge. */
private fun drawTogether(canvas: Canvas, marks: TogetherMarks) {
    val scale = canvas.height / REFERENCE_HEIGHT
    var top = TITLE_CARD_TOP * scale
    for (line in marks.chat) {
        top += drawChatBubble(canvas, line, top, scale) + CARD_LINE_GAP * scale
    }
    marks.reactions.forEach { reaction -> drawReaction(canvas, reaction, scale) }
}

// "Anna  so good" in a dark pill, right-aligned; returns its height.
private fun drawChatBubble(canvas: Canvas, line: TogetherChatLine, top: Float, scale: Float): Float {
    val name = titleCardPaint(CHAT_TEXT_SIZE * scale, LABEL_COLOR, Typeface.BOLD)
    val text = titleCardPaint(CHAT_TEXT_SIZE * scale, Color.WHITE, Typeface.NORMAL)
    val padding = HINT_PADDING_HORIZONTAL * scale * 0.6f
    val nameLabel = line.name + "  "
    val maxTextWidth = canvas.width * CHAT_MAX_WIDTH_FRACTION - name.measureText(nameLabel)
    val shownText = ellipsized(line.text, text, maxTextWidth)
    val width = name.measureText(nameLabel) + text.measureText(shownText) + 2 * padding
    val height = text.fontSpacing + padding
    val right = canvas.width - SIDE_MARGIN * scale
    val bubble = RectF(right - width, top, right, top + height)
    val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    fill.color = CARD_FILL
    canvas.drawRoundRect(bubble, height / 2, height / 2, fill)
    val baseline = bubble.centerY() - (text.ascent() + text.descent()) / 2
    canvas.drawText(nameLabel, bubble.left + padding, baseline, name)
    canvas.drawText(shownText, bubble.left + padding + name.measureText(nameLabel), baseline, text)
    return height
}

// Rises from above the bottom-right corner and fades; three lanes keep a burst apart.
private fun drawReaction(canvas: Canvas, reaction: ReactionMark, scale: Float) {
    val alpha = ((1f - reaction.progress) * 255).toInt().coerceIn(0, 255)
    val emoji = titleCardPaint(REACTION_SIZE * scale, Color.WHITE, Typeface.NORMAL)
    emoji.alpha = alpha
    val name = titleCardPaint(REACTION_NAME_SIZE * scale, LABEL_COLOR, Typeface.BOLD)
    name.alpha = (alpha * Color.alpha(LABEL_COLOR)) / 255
    val centerX = canvas.width - (SIDE_MARGIN + REACTION_LANE_WIDTH * (reaction.lane + 0.5f)) * scale
    val startY = canvas.height * REACTION_START_FRACTION
    val endY = canvas.height * REACTION_END_FRACTION
    val baseline = startY + (endY - startY) * reaction.progress
    emoji.textAlign = Paint.Align.CENTER
    name.textAlign = Paint.Align.CENTER
    canvas.drawText(reaction.emoji, centerX, baseline, emoji)
    canvas.drawText(reaction.name, centerX, baseline + name.fontSpacing, name)
}

private fun titleCardPaint(textSize: Float, color: Int, style: Int): TextPaint {
    val paint = TextPaint(Paint.ANTI_ALIAS_FLAG)
    paint.textSize = textSize
    paint.color = color
    paint.typeface = Typeface.create(Typeface.SANS_SERIF, style)
    return paint
}

private fun ellipsized(text: String, paint: TextPaint, maxWidth: Float): String {
    return TextUtils.ellipsize(text, paint, maxWidth, TextUtils.TruncateAt.END).toString()
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

/** The card's countdown: whole seconds for the text, [progress] (0 to 1) for the fill. */
private data class UpNextCountdown(val secondsLeft: Int, val progress: Float)

// The still on top, then "Next episode in Ns" and the episode, bottom right.
private fun drawUpNextCard(canvas: Canvas, upNext: UpNextHint, countdown: UpNextCountdown, scale: Float) {
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
    val radius = CARD_RADIUS * scale
    drawCardBackground(canvas, card, radius, countdown.progress)
    upNext.still?.let { still -> drawStill(canvas, still, RectF(card.left, card.top, card.right, card.top + stillHeight), radius) }
    val textLeft = card.left + padding
    val titleBaseline = card.top + stillHeight + padding - title.ascent()
    canvas.drawText("Next episode in ${countdown.secondsLeft}s", textLeft, titleBaseline, title)
    val episode = TextUtils.ellipsize(upNext.episodeLabel, label, width - 2 * padding, TextUtils.TruncateAt.END).toString()
    canvas.drawText(episode, textLeft, titleBaseline + title.descent() + CARD_LINE_GAP * scale - label.ascent(), label)
}

// The dark card, filled from the left as the countdown runs, like the phone's Play now button.
private fun drawCardBackground(canvas: Canvas, card: RectF, radius: Float, progress: Float) {
    val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    fill.color = CARD_FILL
    canvas.drawRoundRect(card, radius, radius, fill)
    canvas.save()
    val shape = Path()
    shape.addRoundRect(card, radius, radius, Path.Direction.CW)
    canvas.clipPath(shape)
    fill.color = CARD_PROGRESS
    canvas.drawRect(card.left, card.top, card.left + card.width() * progress, card.bottom, fill)
    canvas.restore()
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
