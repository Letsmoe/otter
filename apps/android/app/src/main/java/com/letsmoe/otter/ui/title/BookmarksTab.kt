package com.letsmoe.otter.ui.title

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.letsmoe.otter.bookmarks.Bookmark
import com.letsmoe.otter.bookmarks.loadBookmarks
import com.letsmoe.otter.bookmarks.removeBookmark
import com.letsmoe.otter.profiles.ProfileSession
import com.letsmoe.otter.ui.components.pressable
import com.letsmoe.otter.ui.player.formatPlayerTime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private val bookmarkMutedColor = Color.White.copy(alpha = 0.55f)

/**
 * The moments the profile bookmarked in a movie, or in a series' episodes; a tap plays from there.
 * [episodeLabelOf] names an episode ("S1:E3 Name"), null for a movie.
 */
@Composable
fun BookmarksTab(titleId: String, episodeLabelOf: (String) -> String?, onPlay: (PlayRequest) -> Unit, modifier: Modifier = Modifier) {
    val profile by ProfileSession.activeProfile.collectAsStateWithLifecycle()
    val profileId = profile?.id ?: return
    var bookmarks by remember { mutableStateOf<List<Bookmark>?>(null) }
    val scope = rememberCoroutineScope()
    // Bookmarks are made in the player; the page shows them again when it comes back.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        scope.launch { bookmarks = fetchBookmarks(profileId, titleId, bookmarks) }
    }
    val shown = bookmarks ?: return
    if (shown.isEmpty()) {
        Text("Bookmark a moment with the bookmark button in the player.", fontSize = 13.sp, color = bookmarkMutedColor, modifier = modifier)
        return
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        for (bookmark in shown) {
            BookmarkRow(bookmark, episodeLabelOf(bookmark.mediaId), onPlay) {
                scope.launch { discard(profileId, bookmark) { bookmarks = shown - bookmark } }
            }
        }
    }
}

// A failed load keeps what was shown.
private suspend fun fetchBookmarks(profileId: String, titleId: String, shown: List<Bookmark>?): List<Bookmark>? {
    return try {
        loadBookmarks(profileId, titleId)
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (error: Exception) {
        shown
    }
}

private suspend fun discard(profileId: String, bookmark: Bookmark, onRemoved: () -> Unit) {
    try {
        removeBookmark(profileId, bookmark.id)
        onRemoved()
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (error: Exception) {
        // The bookmark stays listed.
    }
}

@Composable
private fun BookmarkRow(bookmark: Bookmark, episodeLabel: String?, onPlay: (PlayRequest) -> Unit, onRemove: () -> Unit) {
    val time = formatPlayerTime(bookmark.positionSeconds)
    var heading = time
    if (episodeLabel != null) {
        heading = "$episodeLabel · $time"
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(
            Modifier.weight(1f).pressable { onPlay(PlayRequest(bookmark.mediaId, startSeconds = bookmark.positionSeconds)) },
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(heading, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (!bookmark.caption.isNullOrEmpty()) {
                Text("“${bookmark.caption}”", fontSize = 13.sp, color = bookmarkMutedColor, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        Text("Remove", fontSize = 12.sp, color = bookmarkMutedColor, modifier = Modifier.pressable(onClick = onRemove).padding(8.dp))
    }
}
