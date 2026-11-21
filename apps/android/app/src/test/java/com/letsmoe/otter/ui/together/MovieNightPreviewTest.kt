package com.letsmoe.otter.ui.together

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
import com.letsmoe.otter.graphql.type.TogetherStatus
import com.letsmoe.otter.together.Ballot
import com.letsmoe.otter.together.Suggestion
import com.letsmoe.otter.together.TogetherPeer
import com.letsmoe.otter.together.TogetherRoom
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
 * Renders the movie night sheet's content on the JVM with a room of three. With OTTER_PREVIEW_DIR
 * set, the picture is written there as PNG (docs/previews/movie-night-vote for the pull request).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], application = Application::class, qualifiers = "w360dp-h760dp-xxhdpi")
class MovieNightPreviewTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val anna = TogetherPeer("anna", "Anna", "otter:crown")
    private val ben = TogetherPeer("ben", "Ben", null)
    private val cleo = TogetherPeer("cleo", "Cleo", null)
    private val hostRoom = TogetherRoom("K7QD9MZA", hosting = true, status = TogetherStatus.CONNECTED, self = anna, peers = listOf(anna, ben, cleo))

    private val ballot = Ballot()
        .withSuggestion(Suggestion("movie:157336", "Interstellar", 2014, null, "anna"))
        .withSuggestion(Suggestion("movie:603", "The Matrix", 1999, null, "ben"))
        .withSuggestion(Suggestion("movie:949", "Heat", 1995, null, "cleo"))
        .withVote("anna", "movie:157336")
        .withVote("ben", "movie:603")
        .withVote("cleo", "movie:157336")

    @Test
    fun tappingATitleVotesForIt() {
        var voted: String? = null
        composeRule.setContent { OtterTheme { BallotView(ballot, hostRoom, onVote = { mediaId -> voted = mediaId }) } }

        composeRule.onNode(hasContentDescription("Vote for Heat", substring = true)).performClick()

        assertEquals("movie:949", voted)
    }

    @Test
    fun theBallotShowsWhoSuggestedWhat() {
        composeRule.setContent { OtterTheme { BallotView(ballot, hostRoom, onVote = {}) } }

        composeRule.onNodeWithText("1999 · suggested by Ben").assertExists()
        composeRule.onNodeWithText("2014 · suggested by you").assertExists()
    }

    @Test
    fun yourVoteIsMarkedInTheDescription() {
        composeRule.setContent { OtterTheme { BallotView(ballot, hostRoom, onVote = {}) } }

        composeRule.onNode(hasContentDescription("Vote for Interstellar, 2 votes, your vote")).assertExists()
        composeRule.onNode(hasContentDescription("Vote for The Matrix, 1 vote")).assertExists()
    }

    @Test
    fun writesThePreviewPicture() {
        val directory = System.getenv(PREVIEW_DIRECTORY_VARIABLE)
        if (directory == null) {
            return
        }
        File(directory).mkdirs()
        composeRule.setContent {
            OtterTheme {
                Surface(color = OtterColors.base100) {
                    Box(Modifier.padding(20.dp)) { MovieNightContent(ballot, hostRoom) }
                }
            }
        }
        val bitmap = composeRule.onRoot().captureToImage().asAndroidBitmap()
        File(directory, "movie-night.png").outputStream().use { stream -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream) }
    }
}
