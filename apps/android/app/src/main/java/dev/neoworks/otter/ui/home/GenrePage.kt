package dev.neoworks.otter.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.neoworks.otter.graphql.type.MediaType
import dev.neoworks.otter.ui.media.RailItem
import dev.neoworks.otter.ui.navigation.bottomBarClearance

private const val RAIL_SIZE = 20

/** Replaces the category content once a genre is picked: featured card, then rails of that genre. */
@Composable
fun GenrePage(
    mediaType: MediaType,
    genre: String,
    listState: LazyListState,
    topPadding: Dp,
    ambientColor: Color,
    onFeaturedPoster: (String?) -> Unit,
    onOpenItem: (RailItem) -> Unit,
    chips: @Composable () -> Unit,
) {
    val viewModel = viewModel(key = "genre-$mediaType-$genre") { GenrePageViewModel(mediaType, genre) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val featuredPoster = state.featured?.item?.posterUrl
    LaunchedEffect(featuredPoster) { onFeaturedPoster(featuredPoster) }
    LaunchedEffect(genre, mediaType) { listState.scrollToItem(0) }
    val rows = genreRows(state, genre)
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = topPadding, bottom = bottomBarClearance()),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        item(key = "featured") {
            Column(Modifier.fillMaxWidth().ambientGradient(ambientColor, topPadding), verticalArrangement = Arrangement.spacedBy(16.dp)) {
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
        if (state.loaded && rows.isEmpty()) {
            item(key = "empty") { Text("Nothing in $genre yet", Modifier.padding(horizontal = 16.dp), color = Color.White.copy(alpha = 0.6f)) }
        }
        homeRows(rows, emptyList(), onOpenItem, onOpenPerson = {})
    }
}

private val railTitles = listOf("Popular in %s", "More %s", "Keep Exploring %s", "Deep Cuts in %s")

private fun genreRows(state: GenrePageState, genre: String): List<HomeRow> {
    val featuredId = state.featured?.item?.id
    val titles = state.titles.filter { item -> item.id != featuredId }
    val chunks = titles.chunked(RAIL_SIZE)
    return chunks.take(railTitles.size).mapIndexed { index, chunk ->
        PosterRow("genre-page-$index", railTitles[index].format(genre), chunk, genreRailStyle(index))
    }
}

private fun genreRailStyle(index: Int): RailStyle {
    if (index == 0) {
        return RailStyle.LARGE
    }
    if (index == 2) {
        return RailStyle.WIDE
    }
    return RailStyle.REGULAR
}
