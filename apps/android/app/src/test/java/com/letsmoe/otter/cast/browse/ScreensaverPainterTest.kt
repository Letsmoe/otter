package com.letsmoe.otter.cast.browse

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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
 * Draws the idle TV's picture with the production code on the JVM. With OTTER_PREVIEW_DIR set, the
 * pictures are written there as PNGs (docs/previews/tv-screensaver for the pull request).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], application = Application::class)
class ScreensaverPainterTest {

    private val interstellar = ScreensaverSlide("movie:157336", "Interstellar", 2014, "interstellar")
    private val breakingBad = ScreensaverSlide("series:1396", "Breaking Bad", 2008, "breaking-bad")

    @Test
    fun theTitleSitsSmallInTheBottomLeftCorner() {
        val frame = frameWith(ScreensaverLayer(interstellar, backdrop("interstellar")), null, 0f)

        val corner = brightPixels(frame, left = 96, top = 940, right = 600, bottom = 1000)
        val topRight = brightPixels(frame, left = 1300, top = 60, right = 1800, bottom = 140)
        assertTrue("corner pixels: $corner", corner > 150)
        assertTrue("the label is small: $corner", corner < 5000)
        assertTrue("top right is the backdrop only: $topRight", topRight < corner)
    }

    @Test
    fun theFadeMixesBothBackdrops() {
        val red = ScreensaverLayer(interstellar, solid(Color.rgb(200, 0, 0)))
        val blue = ScreensaverLayer(breakingBad, solid(Color.rgb(0, 0, 200)))

        val frame = frameWith(red, blue, 0.5f)

        val pixel = frame.getPixel(960, 300)
        assertEquals(100.0, Color.red(pixel).toDouble(), 12.0)
        assertEquals(100.0, Color.blue(pixel).toDouble(), 12.0)
    }

    @Test
    fun framesFadeIntoTheNextSlideOnceItsBackdropIsThere() {
        val slides = listOf(interstellar, breakingBad)
        val colors = mapOf("interstellar" to Color.rgb(200, 0, 0), "breaking-bad" to Color.rgb(0, 0, 200))
        val frames = ScreensaverFrames(slides, CoroutineScope(Dispatchers.Unconfined)) { url -> solid(colors.getValue(url)) }
        val bitmap = Bitmap.createBitmap(1920, 1080, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        frames.draw(canvas, 1.0)
        val first = bitmap.getPixel(960, 300)
        frames.draw(canvas, SLIDE_SECONDS - FADE_SECONDS / 2)
        val fading = bitmap.getPixel(960, 300)
        frames.draw(canvas, SLIDE_SECONDS + 1)
        val second = bitmap.getPixel(960, 300)

        assertTrue("first is red: $first", Color.red(first) > 150 && Color.blue(first) < 40)
        assertTrue("middle is both: $fading", Color.red(fading) > 60 && Color.blue(fading) > 60)
        assertTrue("second is blue: $second", Color.blue(second) > 150 && Color.red(second) < 40)
    }

    @Test
    fun writesThePreviewPictures() {
        val directory = System.getenv(PREVIEW_DIRECTORY_VARIABLE)
        if (directory == null) {
            return
        }
        File(directory).mkdirs()
        val current = ScreensaverLayer(interstellar, backdrop("interstellar"))
        val next = ScreensaverLayer(breakingBad, backdrop("breaking-bad"))
        writePng(frameWith(current, null, 0f), File(directory, "screensaver.png"))
        writePng(frameWith(current, next, 0.75f), File(directory, "screensaver-crossfade.png"))
    }

    private fun frameWith(current: ScreensaverLayer, next: ScreensaverLayer?, fade: Float): Bitmap {
        val frame = Bitmap.createBitmap(1920, 1080, Bitmap.Config.ARGB_8888)
        drawScreensaverFrame(Canvas(frame), current, next, fade)
        return frame
    }

    private fun solid(color: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(1920, 1080, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(color)
        return bitmap
    }

    private fun backdrop(name: String): Bitmap {
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
