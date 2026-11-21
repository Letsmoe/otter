package com.letsmoe.otter.ui.player

import com.letsmoe.otter.bookmarks.addBookmark
import com.letsmoe.otter.bookmarks.bookmarkChatText
import com.letsmoe.otter.bookmarks.captionAtMoment
import com.letsmoe.otter.cast.overlay.loadCaptionCues
import com.letsmoe.otter.profiles.ProfileSession
import com.letsmoe.otter.toasts.ToastKind
import com.letsmoe.otter.toasts.Toasts
import com.letsmoe.otter.together.TogetherSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Saves the playing moment as a bookmark, with the caption line spoken then, and tells a Watch Together room. */
class PlayerBookmarks(private val scope: CoroutineScope) {

    /** Bookmarks [positionSeconds] of [mediaId]; [captions] (shifted by [delaySeconds]) name the line, if there are any. */
    fun save(mediaId: String, positionSeconds: Double, captions: PlayerSubtitle?, delaySeconds: Double) {
        val profileId = ProfileSession.activeProfile.value?.id ?: return
        scope.launch {
            val caption = lineAt(captions, positionSeconds, delaySeconds)
            try {
                addBookmark(profileId, mediaId, positionSeconds, caption)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                Toasts.push(error.message ?: "Could not save the bookmark", ToastKind.ERROR)
                return@launch
            }
            val time = formatPlayerTime(positionSeconds)
            Toasts.push("Bookmarked $time", ToastKind.SUCCESS)
            TogetherSession.sendChat(bookmarkChatText(time, caption))
        }
    }

    private suspend fun lineAt(captions: PlayerSubtitle?, positionSeconds: Double, delaySeconds: Double): String? {
        val cues = loadCaptionCues(captions?.srtPath)
        return captionAtMoment(cues, positionSeconds, delaySeconds)
    }
}
