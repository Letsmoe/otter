package dev.neoworks.otter.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import dev.neoworks.otter.ui.media.RailItem
import dev.neoworks.otter.ui.preview.openOrPreview
import dev.neoworks.otter.ui.theme.OtterColors

enum class PosterSize(val width: Dp) {
    REGULAR(112.dp),
    LARGE(160.dp),
}

private val posterShape = RoundedCornerShape(6.dp)
private val posterBackground = Color.White.copy(alpha = 0.05f)

/** Titled row of posters that scrolls sideways; draws nothing without items. */
@Composable
fun PosterRail(
    title: String,
    items: List<RailItem>,
    onOpen: (RailItem) -> Unit,
    modifier: Modifier = Modifier,
    size: PosterSize = PosterSize.REGULAR,
) {
    if (items.isEmpty()) {
        return
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 12.dp))
        LazyRow(
            contentPadding = PaddingValues(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(items, key = { item -> "${item.id}|${item.episodeId}" }) { item ->
                PosterTile(item, onClick = { onOpen(item) }, modifier = Modifier.width(size.width))
            }
        }
    }
}

/** A 2:3 poster with the optional release badge and watch progress. */
@Composable
fun PosterTile(item: RailItem, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .aspectRatio(2f / 3f)
            .clip(posterShape)
            .background(posterBackground)
            .openOrPreview(item, onClick),
    ) {
        PosterImage(item)
        if (item.badge != null) {
            PosterBadge(item.badge, Modifier.align(Alignment.TopStart).padding(6.dp))
        }
        if (item.rank != null) {
            TopTenBadge(Modifier.align(Alignment.TopEnd))
        }
        if (item.label != null && item.progress == null) {
            PosterLabel(item.label, Modifier.align(Alignment.BottomCenter).padding(bottom = 6.dp))
        }
        if (item.progress != null) {
            PosterProgress(item.progress, Modifier.align(Alignment.BottomStart))
        }
    }
}

@Composable
private fun PosterImage(item: RailItem) {
    if (item.posterUrl == null) {
        Box(Modifier.fillMaxSize().padding(8.dp), contentAlignment = Alignment.Center) {
            Text(item.title, fontSize = 12.sp, textAlign = TextAlign.Center)
        }
        return
    }
    AsyncImage(item.posterUrl, contentDescription = item.title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
}

@Composable
private fun PosterBadge(badge: String, modifier: Modifier) {
    Text(
        badge,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(Color.Black.copy(alpha = 0.75f))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

@Composable
private fun PosterProgress(progress: Float, modifier: Modifier) {
    Box(modifier.fillMaxWidth().height(4.dp).background(Color.White.copy(alpha = 0.25f))) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(progress).background(OtterColors.error))
    }
}
