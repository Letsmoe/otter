package com.letsmoe.otter.tv.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.letsmoe.otter.graphql.type.MediaType
import com.letsmoe.otter.tv.theme.TvColors
import com.letsmoe.otter.tv.theme.TvSpacing
import com.letsmoe.otter.tv.theme.TvType
import com.letsmoe.otter.tv.ui.components.TitleName
import com.letsmoe.otter.tv.ui.components.rememberTitleOverview
import androidx.compose.runtime.getValue
import com.letsmoe.otter.ui.media.RailItem

/** What the top of the home screen shows: the focused title, or the featured one before anything is focused. */
data class HeroTitle(val item: RailItem, val genres: List<String> = emptyList())

/**
 * The hero title's backdrop across the top right, fading into black towards the text on the left
 * and into a dark band at the bottom that the rows sit on.
 */
@Composable
fun HeroBackdrop(hero: HeroTitle?, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize()) {
        if (hero != null) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(heroImage(hero.item))
                    .crossfade(400)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .fillMaxWidth(0.78f)
                    .fillMaxHeight(0.8f),
            )
        }
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(0.2f to Color.Black, 0.62f to Color.Transparent)))
        Box(Modifier.fillMaxSize().background(bottomBand))
    }
}

// Opaque enough under the rows that cards and titles never sit on a busy picture.
private val bottomBand = Brush.verticalGradient(
    0.3f to Color.Transparent,
    0.56f to Color.Black.copy(alpha = 0.88f),
    0.72f to Color.Black,
)

/** The hero title's logo (or name), its label like "Recently Added", then year, kind and genres. */
@Composable
fun HeroInfo(hero: HeroTitle?, modifier: Modifier = Modifier) {
    if (hero == null) {
        return
    }
    Column(
        modifier = modifier
            .padding(horizontal = TvSpacing.screenHorizontal)
            .width(480.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TitleName(hero.item.id, hero.item.title, logoMaxWidth = HERO_LOGO_MAX_WIDTH, logoMaxHeight = HERO_LOGO_MAX_HEIGHT)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            HeroLabel(hero.item)
            Text(heroDetails(hero), fontSize = TvType.body, color = TvColors.secondaryContent, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        HeroOverview(hero.item.id)
    }
}

// Logo and two lines of description fit the hero area above the rows.
private val HERO_LOGO_MAX_WIDTH = 360.dp
private val HERO_LOGO_MAX_HEIGHT = 90.dp
private const val OVERVIEW_MAX_LINES = 2

@Composable
private fun HeroOverview(titleId: String) {
    val overview by rememberTitleOverview(titleId)
    val text = overview ?: return
    Text(
        text,
        fontSize = TvType.body,
        lineHeight = 19.sp,
        color = Color.White.copy(alpha = 0.82f),
        maxLines = OVERVIEW_MAX_LINES,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun HeroLabel(item: RailItem) {
    val label = heroLabelOf(item) ?: return
    Text(
        label,
        fontSize = TvType.caption,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .background(Color.White.copy(alpha = 0.16f), RoundedCornerShape(4.dp))
            .padding(horizontal = 7.dp, vertical = 2.dp),
    )
}

// The tag a card shows ("Recently Added") or its badge (an upcoming release date).
private fun heroLabelOf(item: RailItem): String? {
    val label = item.label
    if (label != null) {
        return label
    }
    return item.badge
}

// "2024 · Movie · Drama, Thriller", or the episode for continue watching.
private fun heroDetails(hero: HeroTitle): String {
    val parts = mutableListOf<String>()
    val year = hero.item.year
    if (year != null) {
        parts.add(year.toString())
    }
    parts.add(kindLabel(hero.item.mediaType))
    val subtitle = hero.item.subtitle
    if (hero.item.episodeId != null && subtitle != null) {
        parts.add(subtitle)
    }
    if (hero.genres.isNotEmpty()) {
        parts.add(hero.genres.take(3).joinToString(", "))
    }
    return parts.joinToString("  ·  ")
}

private fun kindLabel(mediaType: MediaType): String {
    if (mediaType == MediaType.SERIES) {
        return "Series"
    }
    return "Movie"
}

private fun heroImage(item: RailItem): String? {
    val backdrop = item.backdropUrl
    if (backdrop != null) {
        return backdrop
    }
    return item.posterUrl
}
