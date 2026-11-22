package com.letsmoe.otter.tv.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.letsmoe.otter.tv.theme.TvColors
import com.letsmoe.otter.tv.theme.TvSpacing
import com.letsmoe.otter.tv.theme.TvType
import com.letsmoe.otter.tv.ui.components.FullBleedBackdrop
import com.letsmoe.otter.tv.ui.components.TitleExtras
import com.letsmoe.otter.tv.ui.components.TitleName
import com.letsmoe.otter.tv.ui.components.creditLine
import com.letsmoe.otter.tv.ui.components.lengthLabel
import com.letsmoe.otter.tv.ui.components.ratingLabel
import com.letsmoe.otter.tv.ui.components.rememberTitleExtras
import com.letsmoe.otter.ui.media.RailItem

/** A title the home hero shows: the featured one or one of the trending titles after it. */
data class HeroTitle(val item: RailItem, val genres: List<String> = emptyList())

/** The hero title's backdrop across the whole screen. */
@Composable
fun HeroBackdrop(hero: HeroTitle?, modifier: Modifier = Modifier) {
    var imageUrl: String? = null
    if (hero != null) {
        imageUrl = heroImage(hero.item)
    }
    FullBleedBackdrop(imageUrl, modifier)
}

/**
 * The hero title's logo (or name), its label like "Recently Added", the facts line (year, rating,
 * runtime or seasons, genres) and who's in it; the description when the credits aren't known.
 */
@Composable
fun HeroInfo(hero: HeroTitle?, modifier: Modifier = Modifier) {
    if (hero == null) {
        return
    }
    val extras by rememberTitleExtras(hero.item.id)
    Column(
        modifier = modifier
            .padding(horizontal = TvSpacing.screenHorizontal)
            .width(HERO_INFO_WIDTH),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        TitleName(
            hero.item.id,
            hero.item.title,
            modifier = Modifier.padding(bottom = 4.dp),
            logoMaxWidth = HERO_LOGO_MAX_WIDTH,
            logoMaxHeight = HERO_LOGO_MAX_HEIGHT,
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            HeroLabel(hero.item)
            Text(heroDetails(hero, extras), fontSize = TvType.detail, fontWeight = FontWeight.SemiBold, color = TvColors.secondaryContent, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        HeroBlurb(heroBlurbOf(extras))
    }
}

private val HERO_INFO_WIDTH = 400.dp
private val HERO_LOGO_MAX_WIDTH = 300.dp
private val HERO_LOGO_MAX_HEIGHT = 96.dp
private const val BLURB_MAX_LINES = 2

@Composable
private fun HeroBlurb(text: String?) {
    if (text == null) {
        return
    }
    Text(
        text,
        fontSize = TvType.detail,
        lineHeight = 17.sp,
        color = Color.White.copy(alpha = 0.85f),
        maxLines = BLURB_MAX_LINES,
        overflow = TextOverflow.Ellipsis,
    )
}

// Who's in it reads at a glance from the sofa; the description is the fallback.
private fun heroBlurbOf(extras: TitleExtras?): String? {
    if (extras == null) {
        return null
    }
    val credits = extras.creditLine()
    if (credits != null) {
        return credits
    }
    return extras.overview
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

// "Drama, Thriller  ·  2024  ·  ★ 7.9  ·  1h 57m", with the episode for continue watching.
private fun heroDetails(hero: HeroTitle, extras: TitleExtras?): String {
    val parts = mutableListOf<String>()
    val genres = heroGenres(hero, extras)
    if (genres.isNotEmpty()) {
        parts.add(genres.take(2).joinToString(", "))
    }
    val year = hero.item.year
    if (year != null) {
        parts.add(year.toString())
    }
    val subtitle = hero.item.subtitle
    if (hero.item.episodeId != null && subtitle != null) {
        parts.add(subtitle)
    }
    if (extras != null) {
        parts.addAll(listOfNotNull(extras.ratingLabel(), extras.lengthLabel(hero.item.mediaType)))
    }
    return parts.joinToString("  ·  ")
}

// Only the featured title comes with its genres; the others' come with the extras.
private fun heroGenres(hero: HeroTitle, extras: TitleExtras?): List<String> {
    if (hero.genres.isNotEmpty() || extras == null) {
        return hero.genres
    }
    return extras.genres
}

private fun heroImage(item: RailItem): String? {
    val backdrop = item.backdropUrl
    if (backdrop != null) {
        return backdrop
    }
    return item.posterUrl
}
