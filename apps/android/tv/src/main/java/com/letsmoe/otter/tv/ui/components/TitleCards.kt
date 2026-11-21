package com.letsmoe.otter.tv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.letsmoe.otter.tv.theme.TvColors
import com.letsmoe.otter.tv.theme.TvType
import com.letsmoe.otter.ui.components.ActorItem
import com.letsmoe.otter.ui.media.RailItem

private val CARD_SHAPE = RoundedCornerShape(8.dp)
private val TOP_TEN_POSTER_WIDTH = 104.dp

/** Room a Top 10 rank number takes left of its poster ("10" is the widest). */
val RANK_NUMBER_WIDTH = 80.dp

/** A 2:3 poster; [onFocus] fires when the D-pad lands on it. */
@Composable
fun PosterCard(item: RailItem, width: Dp, onFocus: () -> Unit, onClick: () -> Unit, modifier: Modifier = Modifier) {
    FocusableSurface(
        onClick = onClick,
        shape = CARD_SHAPE,
        focusedScale = 1.1f,
        modifier = modifier
            .width(width)
            .aspectRatio(2f / 3f)
            .onFocusChanged { state -> reportFocus(state.isFocused, onFocus) },
    ) {
        Box(Modifier.fillMaxSize()) {
            CardImage(item.posterUrl, item.title)
            CardBadge(item.badge, Modifier.align(Alignment.TopStart))
            CardProgress(item.progress, Modifier.align(Alignment.BottomStart))
        }
    }
}

/** A 16:9 backdrop with the title over its bottom edge; continue watching shows progress under it. */
@Composable
fun WideCard(item: RailItem, width: Dp, onFocus: () -> Unit, onClick: () -> Unit, modifier: Modifier = Modifier) {
    FocusableSurface(
        onClick = onClick,
        shape = CARD_SHAPE,
        focusedScale = 1.08f,
        modifier = modifier
            .width(width)
            .aspectRatio(16f / 9f)
            .onFocusChanged { state -> reportFocus(state.isFocused, onFocus) },
    ) {
        Box(Modifier.fillMaxSize()) {
            CardImage(wideImage(item), item.title)
            WideCardCaption(item, Modifier.align(Alignment.BottomStart))
            CardBadge(item.badge, Modifier.align(Alignment.TopStart))
            CardProgress(item.progress, Modifier.align(Alignment.BottomStart))
        }
    }
}

/** A poster with its Top 10 place as a large outlined number on its left. */
@Composable
fun TopTenCard(item: RailItem, rank: Int, onFocus: () -> Unit, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(verticalAlignment = Alignment.Bottom) {
        RankNumber(rank.toString())
        PosterCard(item, width = TOP_TEN_POSTER_WIDTH, onFocus = onFocus, onClick = onClick, modifier = modifier)
    }
}

/** A round portrait with the name underneath. */
@Composable
fun ActorCard(actor: ActorItem, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = Modifier.width(96.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FocusableSurface(onClick = onClick, shape = CircleShape, focusedScale = 1.1f, modifier = modifier.size(80.dp)) {
            AsyncImage(
                model = actor.portraitUrl,
                contentDescription = actor.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().clip(CircleShape),
            )
        }
        Text(
            actor.name,
            fontSize = TvType.detail,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            color = TvColors.secondaryContent,
        )
    }
}

private fun reportFocus(focused: Boolean, onFocus: () -> Unit) {
    if (focused) {
        onFocus()
    }
}

// Episodes and continue watching prefer the backdrop; titles without one fall back to the poster.
private fun wideImage(item: RailItem): String? {
    val backdrop = item.backdropUrl
    if (backdrop != null) {
        return backdrop
    }
    return item.posterUrl
}

@Composable
private fun CardImage(url: String?, title: String) {
    Box(Modifier.fillMaxSize().background(TvColors.base300), contentAlignment = Alignment.Center) {
        // Shown until the image loads, and for titles without one.
        Text(
            title,
            fontSize = TvType.detail,
            textAlign = TextAlign.Center,
            color = TvColors.neutralContent,
            modifier = Modifier.padding(8.dp),
        )
        AsyncImage(model = url, contentDescription = title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
    }
}

@Composable
private fun WideCardCaption(item: RailItem, modifier: Modifier) {
    val shade = Brush.verticalGradient(0f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.85f))
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(shade)
            .padding(horizontal = 10.dp)
            .padding(top = 16.dp, bottom = 9.dp),
    ) {
        Text(item.title, fontSize = TvType.detail, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        val subtitle = item.subtitle
        if (subtitle != null) {
            Text(subtitle, fontSize = TvType.caption, color = TvColors.secondaryContent, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun CardBadge(badge: String?, modifier: Modifier) {
    if (badge == null) {
        return
    }
    Text(
        badge,
        fontSize = TvType.caption,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier
            .padding(6.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(Color.Black.copy(alpha = 0.75f))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

@Composable
private fun CardProgress(progress: Float?, modifier: Modifier) {
    if (progress == null) {
        return
    }
    Box(modifier.fillMaxWidth().height(3.dp).background(Color.White.copy(alpha = 0.25f))) {
        Box(Modifier.fillMaxWidth(progress).height(3.dp).background(TvColors.accent))
    }
}

@Composable
private fun RankNumber(rank: String) {
    Box(Modifier.padding(end = 2.dp)) {
        Text(
            rank,
            // Outlined, so the poster next to it stays the focus.
            style = TextStyle(fontSize = 80.sp, fontWeight = FontWeight.Black, drawStyle = Stroke(width = 4f)),
            color = Color.White.copy(alpha = 0.7f),
        )
    }
}
