package com.letsmoe.otter.ui.title

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.letsmoe.otter.graphql.type.MediaType
import com.letsmoe.otter.notifications.rememberNotificationPermissionRequest
import com.letsmoe.otter.ui.FailedMessage
import com.letsmoe.otter.ui.LoadState
import com.letsmoe.otter.ui.media.RailItem

/**
 * The episode a series page opens on: by id (Continue Watching, notifications) or by its
 * numbers (shared links, which can't know this install's episode ids).
 */
data class LinkedEpisode(val seasonNumber: Int? = null, val episodeId: String? = null, val episodeNumber: Int? = null)

/**
 * A movie or series page, loaded from the id. A series page can open on [linkedEpisode].
 * [onPlay] receives the link to open in the player.
 */
@Composable
fun TitleScreen(
    titleId: String,
    linkedEpisode: LinkedEpisode,
    onPlay: (PlayRequest) -> Unit,
    onBack: () -> Unit,
    onSearch: () -> Unit,
    onOpenItem: (RailItem) -> Unit,
    onOpenPerson: (String) -> Unit,
    slots: TitleSlots = TitleSlots(),
) {
    val titleViewModel = viewModel(key = titleId) { TitleViewModel(titleId) }
    val detail by titleViewModel.detail.collectAsStateWithLifecycle()
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        when (val current = detail) {
            is LoadState.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
            is LoadState.Failed -> FailedMessage(current.message, onRetry = titleViewModel::load)
            is LoadState.Loaded -> LoadedTitle(current.value, titleViewModel, linkedEpisode, onPlay, onBack, onSearch, onOpenItem, onOpenPerson, slots)
        }
    }
}

@Composable
private fun LoadedTitle(
    detail: TitleDetail,
    titleViewModel: TitleViewModel,
    linkedEpisode: LinkedEpisode,
    onPlay: (PlayRequest) -> Unit,
    onBack: () -> Unit,
    onSearch: () -> Unit,
    onOpenItem: (RailItem) -> Unit,
    onOpenPerson: (String) -> Unit,
    slots: TitleSlots,
) {
    val rails by titleViewModel.rails.collectAsStateWithLifecycle()
    val requestNotificationPermission = rememberNotificationPermissionRequest()
    val toggleWatchlist = {
        if (!detail.inWatchlist) {
            requestNotificationPermission()
        }
        titleViewModel.toggleWatchlist()
    }
    val toggleNotifying = {
        if (!detail.notifying) {
            requestNotificationPermission()
        }
        titleViewModel.toggleNotifying()
    }
    val actions = TitlePageActions(onBack, onOpenPerson, onSearch, toggleWatchlist, toggleNotifying, titleViewModel::rate)
    if (detail.mediaType == MediaType.SERIES) {
        SeriesTitle(detail, rails, actions, slots, linkedEpisode, onPlay, onOpenItem)
        return
    }
    val streams by titleViewModel.streams.collectAsStateWithLifecycle()
    MoviePage(detail, streams, rails, titleViewModel, actions, slots, onPlay, onOpenItem)
}

@Composable
private fun SeriesTitle(
    detail: TitleDetail,
    rails: RailsState,
    actions: TitlePageActions,
    slots: TitleSlots,
    linkedEpisode: LinkedEpisode,
    onPlay: (PlayRequest) -> Unit,
    onOpenItem: (RailItem) -> Unit,
) {
    val seriesViewModel = viewModel(key = "series-${detail.id}-$linkedEpisode") {
        SeriesViewModel(detail.id, linkedEpisode)
    }
    val episodes by seriesViewModel.state.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { seriesViewModel.loadEpisodes() }
    SeriesPage(detail, episodes, rails, seriesViewModel, actions, slots, onPlay, onOpenItem)
}
