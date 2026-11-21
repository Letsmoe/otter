package com.letsmoe.otter.tv.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import com.letsmoe.otter.ui.media.RailItem

/** What the top of the home screen shows: the focused title, or the featured one before anything is focused. */
data class HeroTitle(val item: RailItem, val genres: List<String> = emptyList())

/** The hero title's backdrop across the top right, fading into black towards the text and the rows. */
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
                    .fillMaxWidth(0.82f)
                    .fillMaxHeight(0.86f),
            )
        }
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(0.18f to Color.Black, 0.6f to Color.Transparent)))
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.5f to Color.Transparent, 0.86f to Color.Black)))
    }
}

/** Title, year, kind and genres of the hero title. */
@Composable
fun HeroInfo(hero: HeroTitle?, modifier: Modifier = Modifier) {
    if (hero == null) {
        return
    }
    Column(
        modifier = modifier
            .padding(horizontal = TvSpacing.screenHorizontal)
            .width(480.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            hero.item.title,
            fontSize = TvType.heroTitle,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            lineHeight = 34.sp,
        )
        Text(heroDetails(hero), fontSize = TvType.detail, color = TvColors.secondaryContent, maxLines = 1)
    }
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
