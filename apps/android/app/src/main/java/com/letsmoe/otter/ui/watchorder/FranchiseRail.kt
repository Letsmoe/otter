package com.letsmoe.otter.ui.watchorder

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.letsmoe.otter.ui.components.pressable

private val tileWidth = 300.dp

/** Titled row of franchise artwork; a tap opens the franchise's watch-order guide. Draws nothing without guides. */
@Composable
fun FranchiseRail(title: String, guides: List<WatchOrderGuide>, onOpen: (WatchOrderGuide) -> Unit, modifier: Modifier = Modifier) {
    if (guides.isEmpty()) {
        return
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 12.dp))
        LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(guides, key = { guide -> guide.id }) { guide ->
                WatchOrderArtwork(guide, Modifier.width(tileWidth).pressable(onClick = { onOpen(guide) }))
            }
        }
    }
}
