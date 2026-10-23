package dev.neoworks.otter.ui.browse

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.neoworks.otter.ui.components.PosterRail
import dev.neoworks.otter.ui.media.RailItem
import dev.neoworks.otter.ui.navigation.bottomBarClearance

/** The stored movies and shows, each rail linking to its full grid. */
@Composable
fun LibraryScreen(
    onOpenItem: (RailItem) -> Unit,
    onSeeAllMovies: () -> Unit,
    onSeeAllSeries: () -> Unit,
    viewModel: LibraryViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LazyColumn(
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(top = 16.dp, bottom = bottomBarClearance()),
        verticalArrangement = Arrangement.spacedBy(32.dp),
    ) {
        item { Text("Library", fontSize = 30.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 12.dp)) }
        if (state.loaded && state.movies.isEmpty() && state.series.isEmpty()) {
            item { EmptyLibrary() }
        }
        item { SeeAllRail("Movies", "All movies", state.movies, onOpenItem, onSeeAllMovies) }
        item { SeeAllRail("TV Shows", "All TV shows", state.series, onOpenItem, onSeeAllSeries) }
    }
}

@Composable
private fun SeeAllRail(title: String, seeAllLabel: String, items: List<RailItem>, onOpenItem: (RailItem) -> Unit, onSeeAll: () -> Unit) {
    if (items.isEmpty()) {
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        PosterRail(title, items, onOpenItem)
        Text(
            seeAllLabel,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.clickable(onClick = onSeeAll).padding(horizontal = 12.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun EmptyLibrary() {
    Column(Modifier.fillMaxWidth().padding(vertical = 96.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Your library is empty", modifier = Modifier.alpha(0.4f))
        Text(
            "Titles you search for, open or get recommended show up here.",
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.alpha(0.3f).padding(horizontal = 24.dp),
        )
    }
}
