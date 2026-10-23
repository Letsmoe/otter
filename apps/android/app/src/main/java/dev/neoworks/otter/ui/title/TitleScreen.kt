package dev.neoworks.otter.ui.title

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
import dev.neoworks.otter.graphql.type.MediaType
import dev.neoworks.otter.ui.FailedMessage
import dev.neoworks.otter.ui.LoadState
import dev.neoworks.otter.ui.media.RailItem

/**
 * A movie or series page, loaded from the id. A series page can open on a given season and
 * episode ([linkedSeasonNumber], [linkedEpisodeId]). [onPlay] receives the link to open in the player.
 */
@Composable
fun TitleScreen(
    titleId: String,
    linkedSeasonNumber: Int?,
    linkedEpisodeId: String?,
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
            is LoadState.Loaded -> LoadedTitle(current.value, titleViewModel, linkedSeasonNumber, linkedEpisodeId, onPlay, onBack, onSearch, onOpenItem, onOpenPerson, slots)
        }
    }
}

@Composable
private fun LoadedTitle(
    detail: TitleDetail,
    titleViewModel: TitleViewModel,
    linkedSeasonNumber: Int?,
    linkedEpisodeId: String?,
    onPlay: (PlayRequest) -> Unit,
    onBack: () -> Unit,
    onSearch: () -> Unit,
    onOpenItem: (RailItem) -> Unit,
    onOpenPerson: (String) -> Unit,
    slots: TitleSlots,
) {
    val rails by titleViewModel.rails.collectAsStateWithLifecycle()
    val actions = TitlePageActions(onBack, onOpenPerson, onSearch, titleViewModel::toggleWatchlist, titleViewModel::rate)
    if (detail.mediaType == MediaType.SERIES) {
        SeriesTitle(detail, rails, actions, slots, linkedSeasonNumber, linkedEpisodeId, onPlay, onOpenItem)
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
    linkedSeasonNumber: Int?,
    linkedEpisodeId: String?,
    onPlay: (PlayRequest) -> Unit,
    onOpenItem: (RailItem) -> Unit,
) {
    val seriesViewModel = viewModel(key = "series-${detail.id}-$linkedEpisodeId") {
        SeriesViewModel(detail.id, linkedSeasonNumber, linkedEpisodeId)
    }
    val episodes by seriesViewModel.state.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { seriesViewModel.loadEpisodes() }
    SeriesPage(detail, episodes, rails, seriesViewModel, actions, slots, onPlay, onOpenItem)
}
