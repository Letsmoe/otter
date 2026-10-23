package dev.neoworks.otter.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import dev.neoworks.otter.ui.media.RailItem
import dev.neoworks.otter.ui.preview.openOrPreview

private val wideCardWidth = 260.dp
private val wideCardShape = RoundedCornerShape(6.dp)

/** Row of 16:9 backdrop cards with the title over a bottom gradient. */
@Composable
fun WideRail(title: String, items: List<RailItem>, onOpen: (RailItem) -> Unit, modifier: Modifier = Modifier) {
    if (items.isEmpty()) {
        return
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 12.dp))
        LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(items, key = { item -> "${item.id}|${item.episodeId}" }) { item ->
                WideCard(item, onClick = { onOpen(item) }, Modifier.width(wideCardWidth))
            }
        }
    }
}

@Composable
private fun WideCard(item: RailItem, onClick: () -> Unit, modifier: Modifier) {
    val fade = Brush.verticalGradient(0f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.85f))
    Box(
        modifier
            .aspectRatio(16f / 9f)
            .clip(wideCardShape)
            .background(Color.White.copy(alpha = 0.05f))
            .openOrPreview(item, onClick),
    ) {
        AsyncImage(cardImage(item), contentDescription = item.title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(fade).padding(start = 10.dp, end = 10.dp, top = 24.dp, bottom = 8.dp)) {
            Text(item.title, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (item.badge != null) {
            Text(
                item.badge,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.align(Alignment.TopStart).padding(6.dp).clip(RoundedCornerShape(4.dp)).background(Color.Black.copy(alpha = 0.75f)).padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
        if (item.label != null) {
            PosterLabel(item.label, Modifier.align(Alignment.TopEnd).padding(6.dp))
        }
    }
}

private fun cardImage(item: RailItem): String? {
    if (item.backdropUrl != null) {
        return item.backdropUrl
    }
    return item.posterUrl
}
