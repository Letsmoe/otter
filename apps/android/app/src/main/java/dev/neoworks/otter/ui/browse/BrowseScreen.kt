package dev.neoworks.otter.ui.browse

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.neoworks.otter.graphql.type.MediaType
import dev.neoworks.otter.ui.components.FilterSelect
import dev.neoworks.otter.ui.components.MediaCard
import dev.neoworks.otter.ui.components.ScreenHeader
import dev.neoworks.otter.ui.components.WithoutBackdrop
import dev.neoworks.otter.ui.media.RailItem
import dev.neoworks.otter.ui.navigation.bottomBarClearance

private const val ALL_GENRES = ""
private const val LOAD_MORE_DISTANCE = 6

/** Every stored movie or show, filtered by genre, loading further pages while scrolling. */
@Composable
fun BrowseScreen(mediaType: MediaType, title: String, onOpenItem: (RailItem) -> Unit, onBack: () -> Unit) {
    val viewModel = viewModel(key = "browse-$mediaType") { BrowseViewModel(mediaType) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val gridState = rememberLazyGridState()
    LoadMoreNearEnd(gridState, state.items.size, viewModel::loadMore)
    WithoutBackdrop {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            state = gridState,
            modifier = Modifier.fillMaxSize().statusBarsPadding(),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = bottomBarClearance()),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) { ScreenHeader(title, onBack) }
            item(span = { GridItemSpan(maxLineSpan) }) {
                GenreFilter(state.genres, state.genre, viewModel::selectGenre)
            }
            items(state.items, key = { item -> item.id }) { item -> MediaCard(item, onClick = { onOpenItem(item) }) }
            item(span = { GridItemSpan(maxLineSpan) }) { GridFooter(state) }
        }
    }
}

@Composable
private fun LoadMoreNearEnd(gridState: LazyGridState, itemCount: Int, loadMore: () -> Unit) {
    LaunchedEffect(gridState, itemCount) {
        snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .collect { lastVisible ->
                if (lastVisible != null && lastVisible >= itemCount - LOAD_MORE_DISTANCE) {
                    loadMore()
                }
            }
    }
}

@Composable
private fun GenreFilter(genres: List<String>, selected: String, onSelect: (String) -> Unit) {
    val options = listOf(ALL_GENRES to "All genres") + genres.map { genre -> genre to genre }
    Row(Modifier.fillMaxWidth()) {
        FilterSelect("Genre", options, selected, onSelect, showLabel = false)
    }
}

@Composable
private fun GridFooter(state: BrowseState) {
    if (state.loading) {
        Row(Modifier.fillMaxWidth().padding(vertical = 32.dp), horizontalArrangement = Arrangement.Center) {
            CircularProgressIndicator(Modifier.padding(4.dp), strokeWidth = 2.dp)
        }
        return
    }
    if (state.items.isEmpty()) {
        Column(Modifier.fillMaxWidth().padding(vertical = 96.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "Nothing here yet. Titles appear once they were searched, trending or recommended.",
                fontSize = 16.sp,
                fontWeight = FontWeight.Normal,
                textAlign = TextAlign.Center,
                modifier = Modifier.alpha(0.4f),
            )
        }
    }
}
