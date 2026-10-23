package dev.neoworks.otter.ui.title

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.neoworks.otter.ui.components.PosterRail
import dev.neoworks.otter.ui.media.RailItem

/** "More Like This": related titles, the lead's and director's other work, and the genre. */
@Composable
fun RelatedTab(rails: RailsState, onOpenItem: (RailItem) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(24.dp)) {
        for (rail in rails.rails) {
            PosterRail(rail.title, rail.items, onOpenItem)
        }
        if (!rails.loaded) {
            Box(Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(strokeWidth = 2.dp)
            }
        }
        if (rails.loaded && !rails.hasItems) {
            Text("Nothing related yet.", fontSize = 14.sp, color = Color.White.copy(alpha = 0.5f), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp))
        }
    }
}
