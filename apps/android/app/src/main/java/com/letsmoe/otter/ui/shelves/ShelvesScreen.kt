package com.letsmoe.otter.ui.shelves

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Regular
import com.adamglin.phosphoricons.regular.ArrowDown
import com.adamglin.phosphoricons.regular.ArrowLeft
import com.adamglin.phosphoricons.regular.ArrowUp
import com.adamglin.phosphoricons.regular.CaretRight
import com.letsmoe.otter.ui.components.pressable
import com.letsmoe.otter.ui.navigation.bottomBarClearance
import com.letsmoe.otter.ui.title.GlassIconButton
import kotlinx.coroutines.launch

private val cardShape = RoundedCornerShape(16.dp)

/** The profile's shelves: make new ones, reorder them, open one to edit it. */
@Composable
fun ShelvesScreen(onOpenShelf: (String) -> Unit, onBack: () -> Unit) {
    val shelves by ShelfStore.shelves.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { ShelfStore.refresh() }
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().statusBarsPadding(),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = bottomBarClearance()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item { ScreenTitle("My Shelves", onBack) }
            item {
                Text(
                    "Your own rows on Home, above the recommendations. Add titles to a shelf from the preview that opens when you long-press a poster.",
                    fontSize = 14.sp,
                    color = Color.White.copy(alpha = 0.6f),
                )
            }
            item { NewShelfField("Name a new shelf, e.g. Comfort shows") { name -> scope.launch { ShelfStore.create(name) } } }
            itemsIndexed(shelves, key = { _, shelf -> shelf.id }) { index, shelf ->
                ShelfRow(
                    shelf = shelf,
                    canMoveUp = index > 0,
                    canMoveDown = index < shelves.lastIndex,
                    onOpen = { onOpenShelf(shelf.id) },
                    onMove = { offset -> scope.launch { ShelfStore.moveShelf(shelf.id, offset) } },
                )
            }
        }
    }
}

@Composable
internal fun ScreenTitle(title: String, onBack: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        GlassIconButton(PhosphorIcons.Regular.ArrowLeft, "Back", onBack)
        Text(title, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun ShelfRow(shelf: Shelf, canMoveUp: Boolean, canMoveDown: Boolean, onOpen: () -> Unit, onMove: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(cardShape).background(Color.White.copy(alpha = 0.08f)).padding(start = 16.dp, end = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).pressable(onClick = onOpen).padding(vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(shelf.name, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(titleCount(shelf.items.size), fontSize = 12.sp, color = Color.White.copy(alpha = 0.6f))
        }
        MoveButton(PhosphorIcons.Regular.ArrowUp, "Move up", canMoveUp) { onMove(-1) }
        MoveButton(PhosphorIcons.Regular.ArrowDown, "Move down", canMoveDown) { onMove(1) }
        Icon(PhosphorIcons.Regular.CaretRight, contentDescription = null, modifier = Modifier.padding(horizontal = 8.dp).size(18.dp).alpha(0.6f))
    }
}

/** A small arrow that moves a row; dimmed at the ends of the list. */
@Composable
internal fun MoveButton(icon: ImageVector, description: String, enabled: Boolean, onClick: () -> Unit) {
    var alpha = 0.25f
    if (enabled) {
        alpha = 1f
    }
    Icon(
        icon,
        contentDescription = description,
        modifier = Modifier.pressable(enabled = enabled, onClick = onClick).alpha(alpha).padding(10.dp).size(20.dp),
    )
}

fun titleCount(count: Int): String {
    if (count == 1) {
        return "1 title"
    }
    return "$count titles"
}
