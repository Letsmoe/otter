package com.letsmoe.otter.bookmarks

import com.apollographql.apollo.api.Optional
import com.letsmoe.otter.api.OtterApi
import com.letsmoe.otter.api.dataOrThrow
import com.letsmoe.otter.cast.overlay.CaptionCue
import com.letsmoe.otter.graphql.AddBookmarkMutation
import com.letsmoe.otter.graphql.RemoveBookmarkMutation
import com.letsmoe.otter.graphql.TitleBookmarksQuery
import com.letsmoe.otter.graphql.fragment.BookmarkFields

// A line spoken just before the moment still names it, up to this long ago.
private const val RECENT_LINE_SECONDS = 4.0

/** A moment of a movie or episode the profile saved; [caption] is the line spoken then. */
data class Bookmark(val id: String, val mediaId: String, val positionSeconds: Double, val caption: String?)

private fun BookmarkFields.toBookmark(): Bookmark = Bookmark(id, mediaId, positionSeconds, caption)

/** The profile's bookmarks of a movie, or of all episodes of a series, earliest moment first. */
suspend fun loadBookmarks(profileId: String, titleId: String): List<Bookmark> {
    val data = OtterApi.client.query(TitleBookmarksQuery(profileId, titleId)).execute().dataOrThrow()
    return data.bookmarks.map { bookmark -> bookmark.bookmarkFields.toBookmark() }
}

suspend fun addBookmark(profileId: String, mediaId: String, positionSeconds: Double, caption: String?): Bookmark {
    val mutation = AddBookmarkMutation(profileId, mediaId, positionSeconds, Optional.presentIfNotNull(caption))
    return OtterApi.client.mutation(mutation).execute().dataOrThrow().addBookmark.bookmarkFields.toBookmark()
}

suspend fun removeBookmark(profileId: String, bookmarkId: String) {
    OtterApi.client.mutation(RemoveBookmarkMutation(profileId, bookmarkId)).execute().dataOrThrow()
}

/**
 * The caption line to keep with a bookmark at [seconds] (captions shifted by [delaySeconds]): the line
 * on screen, else the one that ended just before; null when there is none.
 */
fun captionAtMoment(cues: List<CaptionCue>, seconds: Double, delaySeconds: Double): String? {
    val captionSeconds = seconds - delaySeconds
    val showing = cues.filter { cue -> captionSeconds >= cue.startSeconds && captionSeconds < cue.endSeconds }
    if (showing.isNotEmpty()) {
        return showing.joinToString(" ") { cue -> cue.text.replace('\n', ' ') }
    }
    val justBefore = cues.lastOrNull { cue -> cue.endSeconds <= captionSeconds && captionSeconds - cue.endSeconds <= RECENT_LINE_SECONDS }
    return justBefore?.text?.replace('\n', ' ')
}

/** What a bookmark says in a Watch Together room's chat, e.g. "Bookmarked 12:34 · “Wake up, Neo.”". */
fun bookmarkChatText(time: String, caption: String?): String {
    if (caption.isNullOrEmpty()) {
        return "Bookmarked $time"
    }
    return "Bookmarked $time · “$caption”"
}
