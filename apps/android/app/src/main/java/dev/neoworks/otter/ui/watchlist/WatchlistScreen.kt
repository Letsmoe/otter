package dev.neoworks.otter.ui.watchlist

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Bold
import com.adamglin.phosphoricons.Regular
import com.adamglin.phosphoricons.bold.X
import com.adamglin.phosphoricons.regular.BookmarkSimple
import com.adamglin.phosphoricons.regular.PencilSimple
import dev.neoworks.otter.graphql.type.MediaType
import dev.neoworks.otter.ui.components.FilterSelect
import dev.neoworks.otter.ui.components.PosterTile
import dev.neoworks.otter.ui.components.SegmentedBar
import dev.neoworks.otter.ui.components.WithoutBackdrop
import dev.neoworks.otter.ui.components.frostedGlass
import dev.neoworks.otter.ui.media.ListOrder
import dev.neoworks.otter.ui.media.RailItem
import dev.neoworks.otter.ui.media.onlyKind
import dev.neoworks.otter.ui.media.sortedBy
import dev.neoworks.otter.ui.navigation.bottomBarClearance

private enum class ListType(val label: String) {
    ALL("All"),
    MOVIES("Movies"),
    SERIES("TV Shows"),
}

private val typeOptions = ListType.entries.map { type -> type to type.label }
private val orderOptions = ListOrder.entries.map { order -> order to order.label }

/** My List: poster grid with a type switch, a sort choice and an edit mode to remove titles. */
@Composable
fun WatchlistScreen(onOpenItem: (RailItem) -> Unit, viewModel: WatchlistViewModel = viewModel()) {
    WithoutBackdrop { WatchlistGrid(onOpenItem, viewModel) }
}

@Composable
private fun WatchlistGrid(onOpenItem: (RailItem) -> Unit, viewModel: WatchlistViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var listType by rememberSaveable { mutableStateOf(ListType.ALL) }
    var order by rememberSaveable { mutableStateOf(ListOrder.ADDED) }
    var editing by rememberSaveable { mutableStateOf(false) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.load() }
    val shownItems = itemsOfType(state.items, listType).sortedBy(order)
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 12.dp, bottom = bottomBarClearance()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        fullWidthItem("type") { SegmentedBar(typeOptions, listType, onSelect = { chosen -> listType = chosen }) }
        fullWidthItem("controls") {
            ListControls(order, { chosen -> order = chosen }, state.items.isNotEmpty(), editing, { editing = !editing })
        }
        items(shownItems, key = { item -> item.id }) { item ->
            EditablePoster(item, editing, onOpen = { onOpenItem(item) }, onRemove = { viewModel.remove(item) })
        }
        if (shownItems.isEmpty() && state.loaded) {
            fullWidthItem("empty") { EmptyList(emptyText(listType)) }
        }
    }
}

private fun LazyGridScope.fullWidthItem(key: String, content: @Composable () -> Unit) {
    item(key = key, span = { GridItemSpan(maxLineSpan) }) { content() }
}

private fun itemsOfType(items: List<RailItem>, type: ListType): List<RailItem> = when (type) {
    ListType.ALL -> items
    ListType.MOVIES -> items.onlyKind(MediaType.MOVIE)
    ListType.SERIES -> items.onlyKind(MediaType.SERIES)
}

private fun emptyText(type: ListType): String = when (type) {
    ListType.MOVIES -> "No movies on your list yet."
    ListType.SERIES -> "No shows on your list yet."
    ListType.ALL -> "Titles you add with + My List show up here."
}

@Composable
private fun ListControls(
    order: ListOrder,
    onOrderChange: (ListOrder) -> Unit,
    canEdit: Boolean,
    editing: Boolean,
    onToggleEditing: () -> Unit,
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        FilterSelect("Sort", orderOptions, order, onOrderChange)
        if (canEdit) {
            var buttonModifier = Modifier.size(40.dp).clip(CircleShape)
            if (editing) {
                buttonModifier = Modifier.size(40.dp).frostedGlass(CircleShape)
            }
            Box(buttonModifier.clickable(onClick = onToggleEditing), contentAlignment = Alignment.Center) {
                Icon(PhosphorIcons.Regular.PencilSimple, contentDescription = "Edit My List", modifier = Modifier.size(22.dp))
            }
        }
    }
}

@Composable
private fun EditablePoster(item: RailItem, editing: Boolean, onOpen: () -> Unit, onRemove: () -> Unit) {
    Box {
        PosterTile(item, onClick = onOpen)
        if (editing) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(32.dp)
                    .frostedGlass(CircleShape)
                    .clickable(onClick = onRemove),
                contentAlignment = Alignment.Center,
            ) {
                Icon(PhosphorIcons.Bold.X, contentDescription = "Remove ${item.title} from My List", modifier = Modifier.size(14.dp))
            }
        }
    }
}

@Composable
private fun EmptyList(text: String) {
    Column(Modifier.fillMaxWidth().padding(top = 64.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(PhosphorIcons.Regular.BookmarkSimple, null, Modifier.size(56.dp).alpha(0.4f))
        Text(text, fontSize = 14.sp, textAlign = TextAlign.Center, modifier = Modifier.alpha(0.6f))
    }
}
