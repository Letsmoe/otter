package com.letsmoe.otter.ui.title

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.letsmoe.otter.ui.theme.OtterColors
import com.letsmoe.otter.ui.theme.OtterTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

private const val PREVIEW_DIRECTORY_VARIABLE = "OTTER_PREVIEW_DIR"

/**
 * Renders the heatmap on the JVM (Robolectric native graphics) with the real ratings of Breaking Bad
 * and the first two and a half seasons watched. With OTTER_PREVIEW_DIR set, the pictures are written there as
 * PNGs (docs/previews/season-heatmap for the pull request).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], application = Application::class, qualifiers = "w360dp-h400dp-xxhdpi")
class SeasonHeatmapPreviewTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val episodes = breakingBadEpisodes()

    @Test
    fun tappingATileNamesTheEpisodeAndPlaysIt() {
        var played: SeasonEpisode? = null
        showHeatmap { episode -> played = episode }

        composeRule.onNode(hasContentDescription("S5:E14", substring = true)).performClick()
        composeRule.onNodeWithText("S5:E14 · Ozymandias").assertExists()
        composeRule.onNodeWithText("Play").performClick()

        assertEquals("Ozymandias", played?.name)
    }

    @Test
    fun aSeriesWithoutRatingsShowsNoHeatmap() {
        composeRule.setContent { SeasonHeatmap(episodes.map { episode -> episode.copy(voteAverage = 0.0) }, onPlay = {}) }

        composeRule.onNodeWithText("Episode ratings").assertDoesNotExist()
    }

    @Test
    fun writesThePreviewPictures() {
        val directory = System.getenv(PREVIEW_DIRECTORY_VARIABLE)
        if (directory == null) {
            return
        }
        File(directory).mkdirs()
        showHeatmap { }
        writePng(File(directory, "heatmap.png"))
        composeRule.onNode(hasContentDescription("S5:E14", substring = true)).performClick()
        writePng(File(directory, "heatmap-selected.png"))
    }

    private fun showHeatmap(onPlay: (SeasonEpisode) -> Unit) {
        composeRule.setContent {
            // As in the app: the Surface sets the light content colour that bare Text needs.
            OtterTheme {
                Surface(color = OtterColors.base100) {
                    Box(Modifier.padding(12.dp)) {
                        SeasonHeatmap(episodes, onPlay)
                    }
                }
            }
        }
    }

    private fun writePng(file: File) {
        val bitmap = composeRule.onRoot().captureToImage().asAndroidBitmap()
        file.outputStream().use { stream -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream) }
    }

    // Season 1 and 2 watched, season 3 up to episode 8, episode 9 half way.
    private fun breakingBadEpisodes(): List<SeasonEpisode> {
        val lines = checkNotNull(javaClass.getResourceAsStream("/heatmap/breaking-bad-episodes.txt")).bufferedReader().readLines()
        return lines.filter { line -> line.isNotBlank() }.map { line ->
            val parts = line.split("|")
            val season = parts[0].toInt()
            val number = parts[1].toInt()
            SeasonEpisode("episode:$season$number", season, number, parts[2], null, null, null, watchedProgress(season, number), null, parts[3].toDouble())
        }
    }

    private fun watchedProgress(season: Int, number: Int): Float? {
        if (season < 3 || (season == 3 && number <= 8)) {
            return 1f
        }
        if (season == 3 && number == 9) {
            return 0.4f
        }
        return null
    }
}
