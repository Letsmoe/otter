package com.letsmoe.otter.ui.shelves

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Regular
import com.adamglin.phosphoricons.regular.ArrowDown
import com.adamglin.phosphoricons.regular.ArrowUp
import com.adamglin.phosphoricons.regular.X
import com.letsmoe.otter.ui.components.PillButton
import com.letsmoe.otter.ui.components.PlainTextField
import com.letsmoe.otter.ui.components.pressable
import com.letsmoe.otter.ui.media.RailItem
import com.letsmoe.otter.ui.navigation.bottomBarClearance
import com.letsmoe.otter.ui.theme.OtterColors
import kotlinx.coroutines.launch

private val rowShape = RoundedCornerShape(12.dp)
private val posterShape = RoundedCornerShape(6.dp)

/** One shelf: rename it, reorder or remove its titles, or delete it. */
@Composable
fun ShelfScreen(shelfId: String, onOpenItem: (RailItem) -> Unit, onBack: () -> Unit) {
    val shelves by ShelfStore.shelves.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { ShelfStore.refresh() }
    val shelf = shelves.firstOrNull { candidate -> candidate.id == shelfId }
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        if (shelf != null) {
            ShelfContent(shelf, onOpenItem, onBack)
        }
    }
}

@Composable
private fun ShelfContent(shelf: Shelf, onOpenItem: (RailItem) -> Unit, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    LazyColumn(
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = bottomBarClearance()),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { ScreenTitle(shelf.name, onBack) }
        item(key = "rename-${shelf.name}") { RenameField(shelf) }
        if (shelf.items.isEmpty()) {
            item { Text("Nothing here yet. Long-press a poster anywhere and choose Add to shelf.", fontSize = 14.sp, color = Color.White.copy(alpha = 0.6f)) }
        }
        itemsIndexed(shelf.items, key = { _, item -> item.id }) { index, item ->
            TitleRow(
                item = item,
                canMoveUp = index > 0,
                canMoveDown = index < shelf.items.lastIndex,
                onOpen = { onOpenItem(item) },
                onMove = { offset -> scope.launch { ShelfStore.moveTitle(shelf.id, item.id, offset) } },
                onRemove = { scope.launch { ShelfStore.remove(shelf.id, item.id) } },
            )
        }
        item {
            DeleteShelfButton {
                scope.launch {
                    if (ShelfStore.delete(shelf.id)) {
                        onBack()
                    }
                }
            }
        }
    }
}

@Composable
private fun RenameField(shelf: Shelf) {
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf(shelf.name) }
    val changed = name.trim() != shelf.name && cleanShelfName(name) != null
    Row(
        Modifier.fillMaxWidth().height(48.dp).clip(rowShape).background(Color.White.copy(alpha = 0.1f)).padding(start = 16.dp, end = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PlainTextField(name, onValueChange = { typed -> name = typed }, placeholder = "Shelf name", modifier = Modifier.weight(1f))
        if (changed) {
            Text(
                "Save",
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.pressable(onClick = { scope.launch { ShelfStore.rename(shelf.id, name) } }).padding(12.dp),
            )
        }
    }
}

@Composable
private fun TitleRow(item: RailItem, canMoveUp: Boolean, canMoveDown: Boolean, onOpen: () -> Unit, onMove: (Int) -> Unit, onRemove: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(rowShape).background(Color.White.copy(alpha = 0.08f)).padding(start = 10.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(Modifier.weight(1f).pressable(onClick = onOpen), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Poster(item)
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(item.title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                item.subtitle?.let { subtitle -> Text(subtitle, fontSize = 12.sp, color = Color.White.copy(alpha = 0.6f)) }
            }
        }
        MoveButton(PhosphorIcons.Regular.ArrowUp, "Move up", canMoveUp) { onMove(-1) }
        MoveButton(PhosphorIcons.Regular.ArrowDown, "Move down", canMoveDown) { onMove(1) }
        MoveButton(PhosphorIcons.Regular.X, "Remove from shelf", true, onRemove)
    }
}

@Composable
private fun Poster(item: RailItem) {
    Box(Modifier.width(44.dp).aspectRatio(2f / 3f).clip(posterShape).background(Color.White.copy(alpha = 0.05f))) {
        if (item.posterUrl != null) {
            AsyncImage(item.posterUrl, contentDescription = item.title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
    }
}

/** Deleting asks for a second tap. */
@Composable
private fun DeleteShelfButton(onDelete: () -> Unit) {
    var confirming by remember { mutableStateOf(false) }
    var label = "Delete shelf"
    if (confirming) {
        label = "Tap again to delete the shelf and its titles"
    }
    Column(Modifier.padding(top = 12.dp)) {
        PillButton(label, Modifier.fillMaxWidth(), enabled = true, filled = false, onClick = {
            if (confirming) {
                onDelete()
            }
            confirming = !confirming
        })
        if (confirming) {
            Text("Titles stay in your library; only the shelf goes.", fontSize = 12.sp, color = OtterColors.error, modifier = Modifier.padding(top = 6.dp))
        }
    }
}
