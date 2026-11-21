package com.letsmoe.otter.ui.watchorder

import kotlin.math.ceil
import kotlin.math.min

/** The artwork is drawn on a 1600x900 canvas and scaled to whatever size it is shown at. */
const val ARTWORK_WIDTH = 1600f
const val ARTWORK_HEIGHT = 900f

const val ARTWORK_MARGIN = 80f
const val ARTWORK_TOP = 64f

/** Films per timeline row; more films wrap into a second row. */
const val FILMS_PER_ROW = 8
const val MAX_POSTERS = FILMS_PER_ROW * 2

private const val STRIP_BOTTOM = 852f
private const val POSTER_ASPECT = 1.5f
private const val MAX_POSTER_WIDTH = 200f
private const val ROW_SPACING = 12f

const val OVERFLOW_SLOT = -1

data class Box(val left: Float, val top: Float, val width: Float, val height: Float) {
    val right: Float get() = left + width
    val bottom: Float get() = top + height
    val centerX: Float get() = left + width / 2f
}

/** One poster of the strip with its timeline circle and year caption. */
data class PosterSlot(
    /** Index into the guide's parts, or [OVERFLOW_SLOT] for the "+N" tile. */
    val partIndex: Int,
    val poster: Box,
    val circleX: Float,
    val circleY: Float,
    val circleRadius: Float,
    val captionBaseline: Float,
    val captionSize: Float,
)

/** A timeline line through the circles of one row. */
data class TimelineRow(val slots: List<PosterSlot>, val y: Float)

data class ArtworkLayout(
    val rows: List<TimelineRow>,
    /** Films that don't fit the strip; shown as a "+N" tile in its last slot. */
    val hiddenCount: Int,
    /** The headline block ends here. */
    val titleBottom: Float,
) {
    val slots: List<PosterSlot> get() = rows.flatMap { row -> row.slots }
}

/** What differs between the one-row and the two-row strip. */
private class RowStyle(
    val gap: Float,
    val circleRadius: Float,
    val timelineGap: Float,
    val captionSize: Float,
    /** Room the headline block needs at least above the strip. */
    val minimumTitleHeight: Float,
) {
    /** Height of a row below its poster: gap, circle, caption. */
    val belowPoster: Float get() = timelineGap + 2 * circleRadius + captionSize * 1.5f
}

private val singleRowStyle = RowStyle(gap = 28f, circleRadius = 22f, timelineGap = 22f, captionSize = 26f, minimumTitleHeight = 330f)
private val doubleRowStyle = RowStyle(gap = 18f, circleRadius = 13f, timelineGap = 14f, captionSize = 20f, minimumTitleHeight = 250f)

/** Places the posters of [partCount] films: one row up to [FILMS_PER_ROW] films, two rows up to [MAX_POSTERS], then a "+N" tile. */
fun layoutArtwork(partCount: Int): ArtworkLayout {
    val shown = min(partCount, MAX_POSTERS)
    val hiddenCount = partCount - shown
    val rowCount = rowCountFor(shown)
    val perRow = ceil(shown / rowCount.toFloat()).toInt().coerceAtLeast(1)
    val style = styleFor(rowCount)
    val posterWidth = posterWidthFor(rowCount, perRow, style)
    val rows = (0 until rowCount).map { rowIndex -> buildRow(rowIndex, perRow, shown, hiddenCount, posterWidth, style, rowCount) }
    return ArtworkLayout(rows, hiddenCount, titleBottom = rows.first().slots.first().poster.top - 36f)
}

private fun rowCountFor(shown: Int): Int {
    if (shown > FILMS_PER_ROW) {
        return 2
    }
    return 1
}

private fun styleFor(rowCount: Int): RowStyle {
    if (rowCount > 1) {
        return doubleRowStyle
    }
    return singleRowStyle
}

private fun posterWidthFor(rowCount: Int, perRow: Int, style: RowStyle): Float {
    val contentWidth = ARTWORK_WIDTH - 2 * ARTWORK_MARGIN
    val widthLimited = (contentWidth - style.gap * (perRow - 1)) / perRow
    val stripHeight = STRIP_BOTTOM - style.minimumTitleHeight - ARTWORK_TOP
    val rowHeight = (stripHeight - ROW_SPACING * (rowCount - 1)) / rowCount
    val heightLimited = (rowHeight - style.belowPoster) / POSTER_ASPECT
    return minOf(widthLimited, heightLimited, MAX_POSTER_WIDTH)
}

private fun buildRow(
    rowIndex: Int,
    perRow: Int,
    shown: Int,
    hiddenCount: Int,
    posterWidth: Float,
    style: RowStyle,
    rowCount: Int,
): TimelineRow {
    val posterHeight = posterWidth * POSTER_ASPECT
    val rowHeight = posterHeight + style.belowPoster
    val stripTop = STRIP_BOTTOM - rowHeight * rowCount - ROW_SPACING * (rowCount - 1)
    val rowTop = stripTop + rowIndex * (rowHeight + ROW_SPACING)
    val circleY = rowTop + posterHeight + style.timelineGap + style.circleRadius
    val captionBaseline = circleY + style.circleRadius + style.captionSize * 1.25f
    val firstIndex = rowIndex * perRow
    val slots = (0 until min(perRow, shown - firstIndex)).map { column ->
        val poster = Box(ARTWORK_MARGIN + column * (posterWidth + style.gap), rowTop, posterWidth, posterHeight)
        val index = firstIndex + column
        var partIndex = index
        if (hiddenCount > 0 && index == shown - 1) {
            partIndex = OVERFLOW_SLOT
        }
        PosterSlot(partIndex, poster, poster.centerX, circleY, style.circleRadius, captionBaseline, style.captionSize)
    }
    return TimelineRow(slots, circleY)
}

data class Crop(val left: Int, val top: Int, val right: Int, val bottom: Int)

/** The part of an image of [width] x [height] that fills a target of [targetAspect] (width over height), centered. */
fun coverCrop(width: Int, height: Int, targetAspect: Float): Crop {
    val imageAspect = width.toFloat() / height
    if (imageAspect > targetAspect) {
        val croppedWidth = (height * targetAspect).toInt()
        val left = (width - croppedWidth) / 2
        return Crop(left, 0, left + croppedWidth, height)
    }
    val croppedHeight = (width / targetAspect).toInt()
    val top = (height - croppedHeight) / 2
    return Crop(0, top, width, top + croppedHeight)
}

/** The headline split into lines at one font size. */
data class HeadlineFit(val lines: List<String>, val fontSize: Float)

private const val MAX_HEADLINE_SIZE = 168f
private const val MIN_HEADLINE_SIZE = 56f
private const val HEADLINE_STEP = 4f
private const val SMALL_HEADLINE_SIZE = 100f
const val HEADLINE_LINE_HEIGHT = 0.94f

/**
 * The largest font size at which [headline] fits in [maxWidth] x [maxHeight] over at most two
 * lines (three at small sizes). [measure] gives a text's width at a font size.
 */
fun fitHeadline(headline: String, maxWidth: Float, maxHeight: Float, measure: (String, Float) -> Float): HeadlineFit {
    var size = MAX_HEADLINE_SIZE
    while (size > MIN_HEADLINE_SIZE) {
        val lines = wrapWords(headline, maxWidth) { text -> measure(text, size) }
        if (lines != null && fits(lines, size, maxHeight)) {
            return HeadlineFit(lines, size)
        }
        size -= HEADLINE_STEP
    }
    val lines = wrapWords(headline, maxWidth) { text -> measure(text, MIN_HEADLINE_SIZE) }
    return HeadlineFit(lines ?: listOf(headline), MIN_HEADLINE_SIZE)
}

private fun fits(lines: List<String>, size: Float, maxHeight: Float): Boolean {
    var allowedLines = 2
    if (size < SMALL_HEADLINE_SIZE) {
        allowedLines = 3
    }
    return lines.size <= allowedLines && lines.size * size * HEADLINE_LINE_HEIGHT <= maxHeight
}

/** Greedy word wrap; null when one word alone is wider than [maxWidth]. */
private fun wrapWords(text: String, maxWidth: Float, measure: (String) -> Float): List<String>? {
    val lines = mutableListOf<String>()
    var current = ""
    for (word in text.split(" ").filter { it.isNotEmpty() }) {
        val candidate = joinWords(current, word)
        if (measure(candidate) <= maxWidth) {
            current = candidate
            continue
        }
        if (current.isNotEmpty()) {
            lines.add(current)
        }
        current = word
        if (measure(current) > maxWidth) {
            return null
        }
    }
    if (current.isNotEmpty()) {
        lines.add(current)
    }
    return lines
}

private fun joinWords(current: String, word: String): String {
    if (current.isEmpty()) {
        return word
    }
    return "$current $word"
}
