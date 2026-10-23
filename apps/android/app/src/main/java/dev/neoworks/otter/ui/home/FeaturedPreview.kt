package dev.neoworks.otter.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.graphics.Brush
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
import dev.neoworks.otter.ui.media.FeaturedItem

private const val MAX_GENRES = 3
private val cardShape = RoundedCornerShape(8.dp)

/** Large poster card at the top of a home page: genres, Play and My List. */
@Composable
fun FeaturedPreview(
    featured: FeaturedItem?,
    inMyList: Boolean,
    onOpen: () -> Unit,
    onToggleMyList: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cardModifier = modifier
        .widthIn(max = 448.dp)
        .fillMaxWidth()
        .aspectRatio(2f / 3f)
        .clip(cardShape)
    if (featured == null) {
        Box(cardModifier.background(Color.White.copy(alpha = 0.05f)))
        return
    }
    Box(cardModifier.border(1.dp, Color.White.copy(alpha = 0.15f), cardShape)) {
        AsyncImage(
            model = featured.item.posterUrl,
            contentDescription = featured.item.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().clickable(onClick = onOpen),
        )
        FeaturedFooter(featured, inMyList, onOpen, onToggleMyList, Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun FeaturedFooter(
    featured: FeaturedItem,
    inMyList: Boolean,
    onOpen: () -> Unit,
    onToggleMyList: () -> Unit,
    modifier: Modifier,
) {
    val fade = Brush.verticalGradient(0f to Color.Transparent, 0.6f to Color.Black.copy(alpha = 0.7f), 1f to Color.Black)
    Column(
        modifier.fillMaxWidth().fillMaxHeight(0.4f).background(fade).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.Bottom),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(featured.genres.take(MAX_GENRES).joinToString(" • "), fontSize = 14.sp, color = Color.White.copy(alpha = 0.85f), textAlign = TextAlign.Center)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FeaturedButton("Play", PhosphorIcons.Fill.Play, Color.White, Color.Black, onOpen, Modifier.weight(1f))
            var myListIcon = PhosphorIcons.Regular.Plus
            if (inMyList) {
                myListIcon = PhosphorIcons.Regular.Check
            }
            FeaturedButton("My List", myListIcon, Color.White.copy(alpha = 0.25f), Color.White, onToggleMyList, Modifier.weight(1f))
        }
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
            .height(44.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(background)
            .clickable(onClick = onClick),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(22.dp))
        Text(label, color = contentColor, fontWeight = FontWeight.SemiBold)
    }
}
