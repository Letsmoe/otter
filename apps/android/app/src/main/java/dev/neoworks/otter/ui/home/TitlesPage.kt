package dev.neoworks.otter.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.neoworks.otter.graphql.type.MediaType
import dev.neoworks.otter.profiles.ProfileSession
import dev.neoworks.otter.ui.media.RailItem
import dev.neoworks.otter.ui.navigation.bottomBarClearance

/** One Home category (movies or TV shows): featured preview, then rails. */
@Composable
fun TitlesPage(
    mediaType: MediaType,
    listState: LazyListState,
    topPadding: Dp,
    ambientColor: Color,
    onFeaturedPoster: (String?) -> Unit,
    onOpenItem: (RailItem) -> Unit,
    onOpenPerson: (String) -> Unit,
    modifier: Modifier = Modifier,
    chips: @Composable () -> Unit = {},
) {
    val profileName = ProfileSession.activeProfile.value?.name.orEmpty()
    val viewModel = viewModel(key = "titles-$mediaType") { TitlesPageViewModel(mediaType) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val rows = state.toRows(labelsOf(mediaType, profileName)).withActorsRow(state.actors.isNotEmpty())
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.loadPersonalRows() }
    val featuredPoster = state.featured?.item?.posterUrl
    LaunchedEffect(featuredPoster) { onFeaturedPoster(featuredPoster) }
    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = topPadding, bottom = bottomBarClearance()),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        item(key = "featured") {
            Column(
                Modifier.fillMaxWidth().ambientGradient(ambientColor, topPadding),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                chips()
                FeaturedPreview(
                    featured = state.featured,
                    inMyList = state.featuredInMyList,
                    onOpen = { state.featured?.let { featured -> onOpenItem(featured.item) } },
                    onToggleMyList = viewModel::toggleFeaturedInMyList,
                    modifier = Modifier.padding(horizontal = 16.dp).align(Alignment.CenterHorizontally),
                )
            }
        }
        homeRows(rows, state.actors, onOpenItem, onOpenPerson)
    }
}

private fun labelsOf(mediaType: MediaType, profileName: String): PageLabels {
    val continueTitle = "Continue Watching for $profileName"
    if (mediaType == MediaType.SERIES) {
        return PageLabels("Shows", "New Episodes", continueTitle)
    }
    return PageLabels("Movies", "New & Coming Soon", continueTitle)
}
