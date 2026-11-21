package com.letsmoe.otter.ui.shelves

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Regular
import com.adamglin.phosphoricons.regular.Check
import com.adamglin.phosphoricons.regular.Plus
import com.letsmoe.otter.ui.components.PlainTextField
import com.letsmoe.otter.ui.components.pressable
import kotlinx.coroutines.launch

private val rowShape = RoundedCornerShape(12.dp)
private val rowBackground = Color.White.copy(alpha = 0.1f)

/** In the preview sheet: the profile's shelves with a check on the ones holding the title; a tap adds or removes it. */
@Composable
fun ShelfPicker(titleId: String, onDone: () -> Unit) {
    val shelves by ShelfStore.shelves.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { ShelfStore.refresh() }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Add to a shelf", fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text("Done", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.pressable(onClick = onDone).padding(8.dp))
        }
        for (shelf in shelves) {
            ShelfChoice(shelf, titleId) { scope.launch { toggle(shelf, titleId) } }
        }
        NewShelfField("New shelf") { name ->
            scope.launch {
                val created = ShelfStore.create(name)
                if (created != null) {
                    ShelfStore.add(created.id, titleId)
                }
            }
        }
    }
}

private suspend fun toggle(shelf: Shelf, titleId: String) {
    if (shelf.holds(titleId)) {
        ShelfStore.remove(shelf.id, titleId)
        return
    }
    ShelfStore.add(shelf.id, titleId)
}

@Composable
private fun ShelfChoice(shelf: Shelf, titleId: String, onClick: () -> Unit) {
    var icon = PhosphorIcons.Regular.Plus
    if (shelf.holds(titleId)) {
        icon = PhosphorIcons.Regular.Check
    }
    Row(
        Modifier.pressable(onClick = onClick).fillMaxWidth().height(48.dp).clip(rowShape).background(rowBackground).padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(shelf.name, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
    }
}

/** A name field with a Create button; clears itself once a name was handed over. */
@Composable
fun NewShelfField(placeholder: String, onCreate: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    val usable = cleanShelfName(name) != null
    Row(
        Modifier.fillMaxWidth().height(48.dp).clip(rowShape).background(rowBackground).padding(start = 16.dp, end = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PlainTextField(name, onValueChange = { typed -> name = typed }, placeholder = placeholder, modifier = Modifier.weight(1f))
        Text(
            "Create",
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.White.copy(alpha = createAlpha(usable)),
            modifier = Modifier.pressable(enabled = usable, onClick = {
                onCreate(name)
                name = ""
            }).padding(12.dp),
        )
    }
}

private fun createAlpha(usable: Boolean): Float {
    if (usable) {
        return 1f
    }
    return 0.35f
}
