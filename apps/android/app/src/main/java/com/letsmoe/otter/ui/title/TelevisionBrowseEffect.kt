package com.letsmoe.otter.ui.title

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import com.letsmoe.otter.cast.browse.TelevisionBrowse
import com.letsmoe.otter.cast.browse.browsePreviewOf

/** While the page is open, a connected TV shows its title. */
@Composable
fun TelevisionBrowseEffect(detail: TitleDetail) {
    LaunchedEffect(detail.id) { TelevisionBrowse.show(browsePreviewOf(detail)) }
    DisposableEffect(detail.id) { onDispose { TelevisionBrowse.leave(detail.id) } }
}
