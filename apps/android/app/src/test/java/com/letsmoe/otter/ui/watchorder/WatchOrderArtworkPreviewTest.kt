package com.letsmoe.otter.ui.watchorder

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Renders the artwork to PNG files, with native graphics on the JVM. Runs only when
 * OTTER_PREVIEW_DIR names the folder to write to; the previews use generated sample images.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class)
class WatchOrderArtworkPreviewTest {

    private val previewDir: File? = System.getenv("OTTER_PREVIEW_DIR")?.let { path -> File(path) }

    private fun render(name: String, franchise: SampleFranchise, watched: Int, progressOfNext: Float = 0f) {
        val directory = previewDir
        assumeTrue("OTTER_PREVIEW_DIR is not set", directory != null)
        directory!!.mkdirs()
        val bitmap = Bitmap.createBitmap(1600, 900, Bitmap.Config.ARGB_8888)
        val guide = SampleGuides.guideOf(franchise, watched, progressOfNext)
        WatchOrderArtworkPainter().draw(Canvas(bitmap), guide, SampleGuides.images(franchise), 1600f)
        File(directory, "$name.png").outputStream().use { stream -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream) }
    }

    @Test
    fun harryPotterHalfWatched() = render("artwork-harry-potter", SampleGuides.harryPotter, watched = 3, progressOfNext = 0.4f)

    @Test
    fun lordOfTheRingsJustStarted() = render("artwork-lord-of-the-rings", SampleGuides.lordOfTheRings, watched = 1)

    @Test
    fun matrixUntouched() = render("artwork-matrix", SampleGuides.matrix, watched = 0)

    @Test
    fun jamesBondWithManyFilms() = render("artwork-james-bond", SampleGuides.jamesBond, watched = 6, progressOfNext = 0.7f)
}
