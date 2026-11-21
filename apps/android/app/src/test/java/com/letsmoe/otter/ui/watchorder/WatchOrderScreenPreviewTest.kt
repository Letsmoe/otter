package com.letsmoe.otter.ui.watchorder

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
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
 * Renders the guide screen (a phone-sized column, one screen high) to PNG files. Runs only when
 * OTTER_PREVIEW_DIR names the folder to write to; the previews use generated sample images.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class, qualifiers = "w411dp-h891dp-xxhdpi")
class WatchOrderScreenPreviewTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val previewDir: File? = System.getenv("OTTER_PREVIEW_DIR")?.let { path -> File(path) }

    private val noActions = WatchOrderActions(onPlay = {}, onOpenFilm = {})

    private fun render(name: String, franchise: SampleFranchise, watched: Int, progressOfNext: Float = 0f) {
        val directory = previewDir
        assumeTrue("OTTER_PREVIEW_DIR is not set", directory != null)
        directory!!.mkdirs()
        val guide = SampleGuides.guideOf(franchise, watched, progressOfNext)
        val images = SampleGuides.images(franchise)
        composeRule.setContent {
            WithArtworkImages(sampleSource(guide, images)) {
                OtterTheme {
                    // The app sits on a Surface, which sets the text colour.
                    Surface(Modifier.width(411.dp).height(891.dp), color = MaterialTheme.colorScheme.background) {
                        WatchOrderContent(guide, noActions)
                    }
                }
            }
        }
        composeRule.waitForIdle()
        val screenshot = composeRule.onRoot().captureToImage().asAndroidBitmap()
        File(directory, "$name.png").outputStream().use { stream -> screenshot.compress(Bitmap.CompressFormat.PNG, 100, stream) }
    }

    @Test
    fun harryPotterGuide() = render("guide-harry-potter", SampleGuides.harryPotter, watched = 3, progressOfNext = 0.4f)

    @Test
    fun matrixGuideNotStarted() = render("guide-matrix", SampleGuides.matrix, watched = 0)

    private fun sampleSource(guide: WatchOrderGuide, images: ArtworkImages): ArtworkImageSource = ArtworkImageSource { _, url, _ ->
        sampleBitmapOf(guide, images, url)
    }

    // The guide's urls are "backdrop" and "poster-<index>", standing in for TMDB's.
    private fun sampleBitmapOf(guide: WatchOrderGuide, images: ArtworkImages, url: String): Bitmap? {
        if (url == "backdrop") {
            return images.backdrop
        }
        val index = guide.parts.indexOfFirst { part -> part.posterUrl == url }
        return images.posters[index]
    }
}
