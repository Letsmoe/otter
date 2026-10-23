package dev.neoworks.otter.ui.profiles

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import dev.neoworks.otter.profiles.AVATAR_SETS
import dev.neoworks.otter.profiles.AvatarSet
import dev.neoworks.otter.profiles.avatarImage
import dev.neoworks.otter.ui.components.ScreenHeader

private const val COLUMNS = 4
private val tileShape = RoundedCornerShape(16.dp)

/**
 * Icon picker: the current icon on top, then one grid per avatar set.
 * Collapsed sets show their first row; tapping the header expands the rest.
 */
@Composable
fun AvatarChooser(selected: String, onBack: () -> Unit, onSelect: (String) -> Unit) {
    val expanded = remember {
        mutableStateMapOf<String, Boolean>().apply {
            AVATAR_SETS.forEach { set -> put(set.key, selected in set.avatars) }
        }
    }
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Choose Icon", onBack)
        LazyColumn(
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { CurrentIcon(selected) }
            items(AVATAR_SETS, key = { set -> set.key }) { set ->
                AvatarSetSection(
                    set = set,
                    selected = selected,
                    expanded = expanded[set.key] == true,
                    onToggle = { expanded[set.key] = expanded[set.key] != true },
                    onSelect = onSelect,
                )
            }
        }
    }
}

@Composable
private fun CurrentIcon(selected: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        AsyncImage(avatarImage(selected, ""), contentDescription = null, modifier = Modifier.size(56.dp).clip(tileShape))
        Text("Current icon", style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.6f))
    }
}

@Composable
private fun AvatarSetSection(
    set: AvatarSet,
    selected: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    onSelect: (String) -> Unit,
) {
    var caretRotation = 0f
    if (expanded) {
        caretRotation = 180f
    }
    val animatedRotation = animateFloatAsState(caretRotation, label = "caret")
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(24.dp))
            .background(Color.White.copy(alpha = 0.05f))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(set.label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text(set.avatars.size.toString(), style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.55f))
            Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null, modifier = Modifier.rotate(animatedRotation.value))
        }
        AvatarRows(set.avatars.take(COLUMNS), selected, onSelect)
        AnimatedVisibility(visible = expanded) {
            AvatarRows(set.avatars.drop(COLUMNS), selected, onSelect)
        }
    }
}

@Composable
private fun AvatarRows(avatarIds: List<String>, selected: String, onSelect: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        for (row in avatarIds.chunked(COLUMNS)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                for (avatarId in row) {
                    AvatarTile(avatarId, avatarId == selected, onSelect, Modifier.weight(1f))
                }
                repeat(COLUMNS - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun AvatarTile(avatarId: String, isSelected: Boolean, onSelect: (String) -> Unit, modifier: Modifier) {
    var tileModifier = modifier.aspectRatio(1f)
    if (isSelected) {
        tileModifier = tileModifier.border(2.dp, Color.White, tileShape).padding(4.dp)
    }
    AsyncImage(
        model = avatarImage(avatarId, ""),
        contentDescription = avatarId,
        modifier = tileModifier.clip(tileShape).clickable { onSelect(avatarId) },
    )
}
