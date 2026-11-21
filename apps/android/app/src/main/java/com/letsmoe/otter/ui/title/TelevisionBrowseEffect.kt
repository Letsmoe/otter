package com.letsmoe.otter.ui.title

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import com.letsmoe.otter.cast.browse.TelevisionBrowse
import com.letsmoe.otter.cast.browse.browsePreviewOf
import com.letsmoe.otter.cast.browse.episodePreviewOf

/** While the page is open, a connected TV shows its title. */
@Composable
fun TelevisionBrowseEffect(detail: TitleDetail) {
    LaunchedEffect(detail.id) { TelevisionBrowse.show(browsePreviewOf(detail)) }
    DisposableEffect(detail.id) { onDispose { TelevisionBrowse.leave(detail.id) } }
}

/** An opened description shows in full on a connected TV; folded again, the TV shows the title as before. */
fun showDescriptionOnTelevision(detail: TitleDetail, expanded: Boolean) {
    var preview = browsePreviewOf(detail)
    if (expanded) {
        preview = preview.copy(fullOverview = true)
    }
    TelevisionBrowse.show(preview)
}

/** An episode's opened description shows on a connected TV with its still and name; folded, the series again. */
fun showEpisodeOnTelevision(detail: TitleDetail, episode: SeasonEpisode, expanded: Boolean) {
    if (!expanded) {
        TelevisionBrowse.show(browsePreviewOf(detail))
        return
    }
    TelevisionBrowse.show(episodePreviewOf(detail, episode))
}
