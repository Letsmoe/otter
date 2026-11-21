package com.letsmoe.otter.ui.yearinotter

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.letsmoe.otter.ui.theme.OtterTheme
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Renders every card of the sample year to PNG files with native graphics on the JVM, as the
 * phone shows them (360x780 dp at 2x, with room left for the story bar and the Share button).
 * Runs only when OTTER_PREVIEW_DIR names the folder to write to; posters are generated samples.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class, qualifiers = "w360dp-h780dp-xhdpi")
class YearCardsPreviewTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val previewDir: File? = System.getenv("OTTER_PREVIEW_DIR")?.let { path -> File(path) }

    private val samplePosters = YearPosterContent { url, modifier ->
        val poster = SampleYear.poster(url)
        if (poster != null) {
            Image(poster.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop, modifier = modifier)
        }
    }

    private fun render(name: String, card: YearCard) {
        val directory = previewDir
        assumeTrue("OTTER_PREVIEW_DIR is not set", directory != null)
        directory!!.mkdirs()
        composeRule.setContent { Phone { YearCardView(card, Modifier.fillMaxSize()) } }
        composeRule.waitForIdle()
        val screenshot = composeRule.onRoot().captureToImage().asAndroidBitmap()
        File(directory, "$name.png").outputStream().use { stream -> screenshot.compress(Bitmap.CompressFormat.PNG, 100, stream) }
    }

    @Composable
    private fun Phone(content: @Composable () -> Unit) {
        CompositionLocalProvider(LocalYearPosters provides samplePosters) {
            OtterTheme {
                Surface(Modifier.width(360.dp).height(780.dp), color = androidx.compose.ui.graphics.Color.Black) {
                    Box(Modifier.fillMaxSize()) { content() }
                }
            }
        }
    }

    private val cards = yearCards(SampleYear.review, "Alex")

    @Test
    fun intro() = render("year-1-intro", cards[0])

    @Test
    fun hours() = render("year-2-hours", cards[1])

    @Test
    fun finished() = render("year-3-finished", cards[2])

    @Test
    fun topSeries() = render("year-4-top-series", cards[3])

    @Test
    fun genres() = render("year-5-genres", cards[4])

    @Test
    fun binge() = render("year-6-binge", cards[5])

    @Test
    fun rewatched() = render("year-7-rewatched", cards[6])

    @Test
    fun firstAndLast() = render("year-8-first-and-last", cards[7])
}
