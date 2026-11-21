package com.letsmoe.otter.cast.browse

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

private const val PREVIEW_DIRECTORY_VARIABLE = "OTTER_PREVIEW_DIR"

/**
 * Draws the TV's browse picture with the production code on the JVM (Robolectric's native
 * graphics). With OTTER_PREVIEW_DIR set, the pictures are written there as PNGs, e.g.
 * docs/previews/tv-browse for the pull request.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
// A plain Application: the app's own would start the backend and the encoders.
@Config(sdk = [36], application = Application::class)
class BrowsePainterTest {

    private val interstellar = BrowsePreview(
        titleId = "movie:157336",
        title = "Interstellar",
        facts = listOf("2014", "2h 49m", "★ 8.5", "Adventure · Drama"),
        overview = "The adventures of a group of explorers who make use of a newly discovered wormhole to " +
            "surpass the limitations on human space travel and conquer the vast distances involved in an interstellar voyage.",
        backdropUrl = null,
    )

    private val breakingBad = BrowsePreview(
        titleId = "series:1396",
        title = "Breaking Bad",
        facts = listOf("2008", "5 Seasons", "★ 8.9", "Drama · Crime"),
        overview = "Walter White, a New Mexico chemistry teacher, is diagnosed with Stage III cancer and given a " +
            "prognosis of only two years left to live. He becomes filled with a sense of fearlessness and an " +
            "unrelenting desire to secure his family's financial future at any cost as he enters the dangerous world of drugs and crime.",
        backdropUrl = null,
    )

    private val longTitleWithoutOverview = BrowsePreview(
        titleId = "series:95396",
        title = "Severance: Season Two, the Long Way Home to Lumon Industries",
        facts = listOf("2022", "2 Seasons", "★ 8.4", "Drama · Mystery"),
        overview = null,
        backdropUrl = null,
    )

    @Test
    fun theFrameHasTheTelevisionsSize() {
        val frame = renderBrowseFrame(interstellar, backdrop(), 1080)

        assertEquals(1920, frame.width)
        assertEquals(1080, frame.height)
    }

    @Test
    fun theTitleIsDrawnWhiteOverTheShadedLowerLeft() {
        val frame = renderBrowseFrame(interstellar, backdrop(), 1080)

        val titleArea = brightPixels(frame, left = 128, top = 600, right = 900, bottom = 760)
        val emptyCorner = brightPixels(frame, left = 1500, top = 900, right = 1900, bottom = 1060)
        assertTrue("title pixels: $titleArea", titleArea > 500)
        assertTrue("corner pixels: $emptyCorner", emptyCorner < titleArea / 10)
    }

    @Test
    fun aTitleWithoutBackdropOrOverviewStillDraws() {
        val frame = renderBrowseFrame(longTitleWithoutOverview, null, 720)

        assertEquals(1280, frame.width)
        assertTrue(brightPixels(frame, left = 80, top = 400, right = 900, bottom = 700) > 100)
    }

    @Test
    fun aPosterSitsOnTheRightWhenThereIsNoBackdrop() {
        val poster = Bitmap.createBitmap(200, 300, Bitmap.Config.ARGB_8888)
        poster.eraseColor(Color.rgb(200, 40, 40))

        val frame = renderBrowseFrame(interstellar, poster, 1080)

        val pixel = frame.getPixel(1500, 540)
        assertTrue("poster pixel is $pixel", Color.red(pixel) > 190 && Color.green(pixel) < 50)
    }

    @Test
    fun writesThePreviewPictures() {
        val directory = System.getenv(PREVIEW_DIRECTORY_VARIABLE)
        if (directory == null) {
            return
        }
        File(directory).mkdirs()
        writePng(renderBrowseFrame(interstellar, backdrop("interstellar"), 1080), File(directory, "interstellar.png"))
        writePng(renderBrowseFrame(breakingBad, backdrop("breaking-bad"), 1080), File(directory, "breaking-bad.png"))
    }

    private fun backdrop(name: String = "interstellar"): Bitmap {
        val stream = javaClass.getResourceAsStream("/browse/$name-backdrop.jpg")
        return BitmapFactory.decodeStream(checkNotNull(stream))
    }

    private fun brightPixels(frame: Bitmap, left: Int, top: Int, right: Int, bottom: Int): Int {
        var count = 0
        for (y in top until bottom step 2) {
            for (x in left until right step 2) {
                if (Color.red(frame.getPixel(x, y)) > 235 && Color.green(frame.getPixel(x, y)) > 235) {
                    count++
                }
            }
        }
        return count
    }

    private fun writePng(frame: Bitmap, file: File) {
        file.outputStream().use { stream -> frame.compress(Bitmap.CompressFormat.PNG, 100, stream) }
    }
}
