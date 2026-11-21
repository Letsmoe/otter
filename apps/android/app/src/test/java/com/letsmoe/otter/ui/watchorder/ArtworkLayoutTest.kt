package com.letsmoe.otter.ui.watchorder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ArtworkLayoutTest {

    private fun assertInsideCanvas(layout: ArtworkLayout) {
        for (slot in layout.slots) {
            val poster = slot.poster
            assertTrue("poster left ${poster.left}", poster.left >= ARTWORK_MARGIN - 0.01f)
            assertTrue("poster right ${poster.right}", poster.right <= ARTWORK_WIDTH - ARTWORK_MARGIN + 0.01f)
            assertTrue("caption ${slot.captionBaseline}", slot.captionBaseline <= ARTWORK_HEIGHT)
            assertTrue("poster top ${poster.top}", poster.top > layout.titleBottom)
        }
    }

    @Test
    fun fewFilmsShareOneRowInReleaseOrder() {
        val layout = layoutArtwork(3)
        assertEquals(1, layout.rows.size)
        assertEquals(listOf(0, 1, 2), layout.slots.map { slot -> slot.partIndex })
        assertTrue(layout.slots[0].poster.left < layout.slots[1].poster.left)
        assertEquals(0, layout.hiddenCount)
        assertInsideCanvas(layout)
    }

    @Test
    fun eightFilmsStillFitOneRow() {
        val layout = layoutArtwork(FILMS_PER_ROW)
        assertEquals(1, layout.rows.size)
        assertInsideCanvas(layout)
    }

    @Test
    fun moreThanEightFilmsWrapIntoTwoRows() {
        val layout = layoutArtwork(10)
        assertEquals(2, layout.rows.size)
        assertEquals(10, layout.slots.size)
        assertTrue(layout.rows[1].y > layout.rows[0].y)
        assertInsideCanvas(layout)
    }

    @Test
    fun filmsBeyondTheStripBecomeAMoreTile() {
        val layout = layoutArtwork(25)
        assertEquals(MAX_POSTERS, layout.slots.size)
        assertEquals(25 - MAX_POSTERS, layout.hiddenCount)
        assertEquals(OVERFLOW_SLOT, layout.slots.last().partIndex)
        assertEquals(MAX_POSTERS - 2, layout.slots[MAX_POSTERS - 2].partIndex)
        assertInsideCanvas(layout)
    }

    @Test
    fun postersKeepTheTwoToThreeShape() {
        for (count in listOf(2, 5, 8, 9, 16)) {
            val poster = layoutArtwork(count).slots.first().poster
            assertEquals(1.5f, poster.height / poster.width, 0.01f)
        }
    }

    @Test
    fun headlineGetsTheLargestSizeThatFits() {
        // Every character is as wide as the font size, so "MATRIX" is 6 em wide.
        val fit = fitHeadline("MATRIX", maxWidth = 1200f, maxHeight = 400f) { text, size -> text.length * size }
        assertEquals(listOf("MATRIX"), fit.lines)
        assertEquals(168f, fit.fontSize, 0.01f)
    }

    @Test
    fun longHeadlineShrinksOrWraps() {
        val fit = fitHeadline("THE LORD OF THE RINGS", maxWidth = 1440f, maxHeight = 226f) { text, size -> text.length * size * 0.6f }
        assertTrue(fit.lines.size <= 2)
        assertTrue(fit.lines.all { line -> line.length * fit.fontSize * 0.6f <= 1440f })
        assertTrue(fit.lines.size * fit.fontSize * HEADLINE_LINE_HEIGHT <= 226f)
    }

    @Test
    fun headlineWrapsAtWordBoundaries() {
        val fit = fitHeadline("ONE TWO", maxWidth = 500f, maxHeight = 600f) { text, size -> text.length * size }
        assertEquals(listOf("ONE", "TWO"), fit.lines)
    }

    @Test
    fun coverCropCentersTheLongerSide() {
        assertEquals(Crop(200, 0, 600, 300), coverCrop(800, 300, targetAspect = 400f / 300f))
        assertEquals(Crop(0, 100, 400, 500), coverCrop(400, 600, targetAspect = 1f))
    }
}
