package dev.neoworks.otter.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Regular
import com.adamglin.phosphoricons.regular.Check
import dev.neoworks.otter.ui.components.GlassBottomSheet
import dev.neoworks.otter.ui.media.MediaRepository

/** Glass sheet listing the genres; picking one opens that genre's page. */
@Composable
fun CategoriesSheet(selectedGenre: String?, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    var genres by remember { mutableStateOf<List<String>?>(null) }
    LaunchedEffect(Unit) { genres = MediaRepository.genres() }
    GlassBottomSheet(onDismiss) {
        Text("Categories", fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 12.dp))
        val loaded = genres
        if (loaded == null) {
            Text("Loading...", color = Color.White.copy(alpha = 0.6f), modifier = Modifier.padding(vertical = 16.dp))
            return@GlassBottomSheet
        }
        if (loaded.isEmpty()) {
            Text("No categories yet", color = Color.White.copy(alpha = 0.6f), modifier = Modifier.padding(vertical = 16.dp))
            return@GlassBottomSheet
        }
        GenreList(loaded, selectedGenre, onPick)
    }
}

@Composable
private fun ColumnScope.GenreList(genres: List<String>, selectedGenre: String?, onPick: (String) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        for (genre in genres) {
            GenreRow(genre, genre == selectedGenre, onClick = { onPick(genre) })
        }
    }
}

@Composable
private fun GenreRow(genre: String, isSelected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        var weight = FontWeight.Normal
        if (isSelected) {
            weight = FontWeight.Bold
        }
        Text(genre, fontSize = 17.sp, fontWeight = weight)
        if (isSelected) {
            Icon(PhosphorIcons.Regular.Check, contentDescription = "Selected", modifier = Modifier.size(20.dp))
        }
    }
}
