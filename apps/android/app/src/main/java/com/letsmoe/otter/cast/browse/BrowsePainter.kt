package com.letsmoe.otter.cast.browse

import android.content.Context
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
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import com.letsmoe.otter.cast.session.drawWordmark

// Sizes are in pixels of a 1080-line picture and scale with the real one.
private const val REFERENCE_HEIGHT = 1080f
private const val PICTURE_RATIO = 16f / 9f
private const val SIDE_MARGIN = 128f
private const val BOTTOM_MARGIN = 120f
private const val TOP_MARGIN = 96f
private const val TEXT_MAX_WIDTH = 1180f
private const val OVERVIEW_MAX_WIDTH = 1020f
private val TITLE_SIZES = floatArrayOf(112f, 92f, 76f)
private const val TITLE_MAX_LINES = 2
private const val FACTS_SIZE = 38f
private const val OVERVIEW_SIZE = 36f
private const val OVERVIEW_MAX_LINES = 3
private const val OVERVIEW_LINE_SPACING = 1.3f
private const val FACTS_GAP = 26f
private const val OVERVIEW_GAP = 30f
private const val ELLIPSIS = "…"
private const val BRAND_HEIGHT = 30f
private const val BRAND_ALPHA = 150
private const val POSTER_HEIGHT_FRACTION = 0.74f
private const val POSTER_RADIUS = 28f
private val BASE_COLOR = Color.rgb(11, 11, 13)
private val MUTED_WHITE = Color.argb(184, 255, 255, 255)
private val SOFT_WHITE = Color.argb(214, 255, 255, 255)

/** Renders the TV's picture of [preview] over its [backdrop], [height] lines tall (16:9). */
fun renderBrowseFrame(context: Context, preview: BrowsePreview, backdrop: Bitmap?, height: Int): Bitmap {
    val width = (height * PICTURE_RATIO).toInt()
    val picture = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    drawBrowseFrame(Canvas(picture), context, preview, backdrop)
    return picture
}

/**
 * A calm big-screen preview: the backdrop full screen under a dark shade, the
 * title large at the bottom left with its facts and the start of its overview,
 * and the app's wordmark at the top.
 */
fun drawBrowseFrame(canvas: Canvas, context: Context, preview: BrowsePreview, backdrop: Bitmap?) {
    val scale = canvas.height / REFERENCE_HEIGHT
    canvas.drawColor(BASE_COLOR)
    if (backdrop != null && !isPortrait(backdrop)) {
        drawBackdrop(canvas, backdrop)
    }
    drawShade(canvas)
    if (backdrop != null && isPortrait(backdrop)) {
        drawPoster(canvas, backdrop, scale)
    }
    drawWordmark(canvas, context, SIDE_MARGIN * scale, TOP_MARGIN * scale, BRAND_HEIGHT * scale, BRAND_ALPHA)
    drawTitleBlock(canvas, preview, scale)
}

private fun isPortrait(picture: Bitmap): Boolean = picture.height > picture.width

private fun drawBackdrop(canvas: Canvas, backdrop: Bitmap) {
    val slot = RectF(0f, 0f, canvas.width.toFloat(), canvas.height.toFloat())
    canvas.drawBitmap(backdrop, centerCrop(backdrop, slot), slot, Paint(Paint.FILTER_BITMAP_FLAG))
}

// Titles without a backdrop only have a portrait poster: it sits on the right as a card.
private fun drawPoster(canvas: Canvas, poster: Bitmap, scale: Float) {
    val height = canvas.height * POSTER_HEIGHT_FRACTION
    val width = height * poster.width / poster.height
    val right = canvas.width - SIDE_MARGIN * scale
    val top = (canvas.height - height) / 2
    val slot = RectF(right - width, top, right, top + height)
    val clip = Path()
    clip.addRoundRect(slot, POSTER_RADIUS * scale, POSTER_RADIUS * scale, Path.Direction.CW)
    canvas.save()
    canvas.clipPath(clip)
    canvas.drawBitmap(poster, Rect(0, 0, poster.width, poster.height), slot, Paint(Paint.FILTER_BITMAP_FLAG))
    canvas.restore()
}

// Dark from the left and from the bottom, where the text is, so any backdrop keeps it readable.
private fun drawShade(canvas: Canvas) {
    val width = canvas.width.toFloat()
    val height = canvas.height.toFloat()
    val fromLeft = Paint()
    fromLeft.shader = LinearGradient(
        0f, 0f, width * 0.78f, 0f,
        intArrayOf(Color.argb(224, 8, 8, 10), Color.argb(150, 8, 8, 10), Color.TRANSPARENT),
        floatArrayOf(0f, 0.55f, 1f),
        Shader.TileMode.CLAMP,
    )
    canvas.drawRect(0f, 0f, width, height, fromLeft)
    val fromBottom = Paint()
    fromBottom.shader = LinearGradient(
        0f, height * 0.3f, 0f, height,
        intArrayOf(Color.TRANSPARENT, Color.argb(190, 8, 8, 10), Color.argb(245, 8, 8, 10)),
        floatArrayOf(0f, 0.6f, 1f),
        Shader.TileMode.CLAMP,
    )
    canvas.drawRect(0f, 0f, width, height, fromBottom)
    val fromTop = Paint()
    fromTop.shader = LinearGradient(0f, 0f, 0f, height * 0.22f, Color.argb(150, 8, 8, 10), Color.TRANSPARENT, Shader.TileMode.CLAMP)
    canvas.drawRect(0f, 0f, width, height * 0.22f, fromTop)
}

/** The text block, its last line ending [BOTTOM_MARGIN] above the picture's bottom. */
private fun drawTitleBlock(canvas: Canvas, preview: BrowsePreview, scale: Float) {
    val title = titleLayout(preview.title, scale)
    val facts = factsLayout(preview.factsLine, scale)
    val overview = overviewLayout(preview.overview, scale)
    var top = canvas.height - BOTTOM_MARGIN * scale - blockHeight(title, facts, overview, scale)
    top = drawLayout(canvas, title, top)
    if (facts != null) {
        top = drawLayout(canvas, facts, top + FACTS_GAP * scale)
    }
    if (overview != null) {
        drawLayout(canvas, overview, top + OVERVIEW_GAP * scale)
    }
}

private fun blockHeight(title: StaticLayout, facts: StaticLayout?, overview: StaticLayout?, scale: Float): Float {
    var height = title.height.toFloat()
    if (facts != null) {
        height += FACTS_GAP * scale + facts.height
    }
    if (overview != null) {
        height += OVERVIEW_GAP * scale + overview.height
    }
    return height
}

// Draws the layout with its top at [top]; returns where it ends.
private fun drawLayout(canvas: Canvas, layout: StaticLayout, top: Float): Float {
    canvas.save()
    canvas.translate(SIDE_MARGIN * canvas.height / REFERENCE_HEIGHT, top)
    layout.draw(canvas)
    canvas.restore()
    return top + layout.height
}

// The largest size that keeps the title within two lines; the smallest one cuts it off.
private fun titleLayout(title: String, scale: Float): StaticLayout {
    val layouts = TITLE_SIZES.map { size -> textLayout(title, titlePaint(size * scale), TEXT_MAX_WIDTH * scale, TITLE_MAX_LINES) }
    return layouts.firstOrNull { layout -> !isCutOff(layout, title) } ?: layouts.last()
}

private fun factsLayout(factsLine: String, scale: Float): StaticLayout? {
    if (factsLine.isEmpty()) {
        return null
    }
    return textLayout(factsLine, textPaint(FACTS_SIZE * scale, MUTED_WHITE, Typeface.BOLD), TEXT_MAX_WIDTH * scale, 1)
}

private fun overviewLayout(overview: String?, scale: Float): StaticLayout? {
    if (overview.isNullOrEmpty()) {
        return null
    }
    val paint = textPaint(OVERVIEW_SIZE * scale, SOFT_WHITE, Typeface.NORMAL)
    val width = OVERVIEW_MAX_WIDTH * scale
    val fitted = textLayout(overview, paint, width, Int.MAX_VALUE, OVERVIEW_LINE_SPACING)
    if (fitted.lineCount <= OVERVIEW_MAX_LINES) {
        return fitted
    }
    val shortened = shortenedAtWord(overview, fitted.getLineEnd(OVERVIEW_MAX_LINES - 1))
    return textLayout(shortened, paint, width, OVERVIEW_MAX_LINES, OVERVIEW_LINE_SPACING)
}

// The text up to [end] without its last word, which makes room for the ellipsis.
private fun shortenedAtWord(text: String, end: Int): String {
    val kept = text.substring(0, end).trimEnd()
    val lastSpace = kept.lastIndexOf(' ')
    if (lastSpace <= 0) {
        return kept + ELLIPSIS
    }
    return kept.substring(0, lastSpace).trimEnd(' ', ',', ';', ':', '.', '-') + ELLIPSIS
}

private fun titlePaint(size: Float): TextPaint = textPaint(size, Color.WHITE, Typeface.BOLD)

internal fun textPaint(size: Float, color: Int, style: Int): TextPaint {
    val paint = TextPaint(Paint.ANTI_ALIAS_FLAG)
    paint.textSize = size
    paint.typeface = Typeface.create(Typeface.SANS_SERIF, style)
    paint.color = color
    return paint
}

internal fun textLayout(text: String, paint: TextPaint, width: Float, maxLines: Int, lineSpacing: Float = 1f): StaticLayout {
    return StaticLayout.Builder.obtain(text, 0, text.length, paint, width.toInt())
        .setAlignment(Layout.Alignment.ALIGN_NORMAL)
        .setMaxLines(maxLines)
        .setEllipsize(TextUtils.TruncateAt.END)
        .setLineSpacing(0f, lineSpacing)
        .build()
}

private fun isCutOff(layout: StaticLayout, text: String): Boolean {
    val lastLine = layout.lineCount - 1
    return layout.getEllipsisCount(lastLine) > 0 || layout.getLineEnd(lastLine) < text.length
}

internal fun centerCrop(picture: Bitmap, slot: RectF): Rect {
    val slotRatio = slot.width() / slot.height()
    val pictureRatio = picture.width.toFloat() / picture.height
    if (pictureRatio > slotRatio) {
        val croppedWidth = (picture.height * slotRatio).toInt()
        val left = (picture.width - croppedWidth) / 2
        return Rect(left, 0, left + croppedWidth, picture.height)
    }
    val croppedHeight = (picture.width / slotRatio).toInt()
    val top = (picture.height - croppedHeight) / 2
    return Rect(0, top, picture.width, top + croppedHeight)
}
