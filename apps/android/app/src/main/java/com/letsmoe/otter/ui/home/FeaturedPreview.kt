package com.letsmoe.otter.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Fill
import com.adamglin.phosphoricons.Regular
import com.adamglin.phosphoricons.fill.Play
import com.adamglin.phosphoricons.regular.Check
import com.adamglin.phosphoricons.regular.Plus
import com.letsmoe.otter.ui.components.PRESSED_SCALE_LARGE
import com.letsmoe.otter.ui.components.pressable
import com.letsmoe.otter.ui.media.FeaturedItem

private const val MAX_GENRES = 3
private val cardShape = RoundedCornerShape(8.dp)

/**
 * Large poster at the top of a home page with its genres, Play and My List below it, so nothing
 * covers the title printed on the poster. The poster opens the title, Play plays it.
 */
@Composable
fun FeaturedPreview(
    featured: FeaturedItem?,
    inMyList: Boolean,
    onOpen: () -> Unit,
    onPlay: () -> Unit,
    onToggleMyList: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.widthIn(max = 448.dp).fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        FeaturedPoster(featured, onOpen)
        if (featured != null) {
            FeaturedFooter(featured, inMyList, onPlay, onToggleMyList)
        }
    }
}

@Composable
private fun FeaturedPoster(featured: FeaturedItem?, onOpen: () -> Unit) {
    val posterModifier = Modifier.fillMaxWidth().aspectRatio(2f / 3f).clip(cardShape)
    if (featured == null) {
        Box(posterModifier.background(Color.White.copy(alpha = 0.05f)))
        return
    }
    AsyncImage(
        model = featured.item.posterUrl,
        contentDescription = featured.item.title,
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .pressable(pressedScale = PRESSED_SCALE_LARGE, onClick = onOpen)
            .then(posterModifier)
            .border(1.dp, Color.White.copy(alpha = 0.15f), cardShape),
    )
}

@Composable
private fun FeaturedFooter(
    featured: FeaturedItem,
    inMyList: Boolean,
    onPlay: () -> Unit,
    onToggleMyList: () -> Unit,
) {
    Text(featured.genres.take(MAX_GENRES).joinToString(" • "), fontSize = 14.sp, color = Color.White.copy(alpha = 0.85f), textAlign = TextAlign.Center)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        FeaturedButton("Play", PhosphorIcons.Fill.Play, Color.White, Color.Black, onPlay, Modifier.weight(1f))
        var myListIcon = PhosphorIcons.Regular.Plus
        if (inMyList) {
            myListIcon = PhosphorIcons.Regular.Check
        }
        FeaturedButton("My List", myListIcon, Color.White.copy(alpha = 0.25f), Color.White, onToggleMyList, Modifier.weight(1f))
    }
}

@Composable
private fun FeaturedButton(
    label: String,
    icon: ImageVector,
    background: Color,
    contentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    Row(
        modifier
            .pressable(onClick = onClick)
            .height(44.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(background),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(22.dp))
        Text(label, color = contentColor, fontWeight = FontWeight.SemiBold)
    }
}
