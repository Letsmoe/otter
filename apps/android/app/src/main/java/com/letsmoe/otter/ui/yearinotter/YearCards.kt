package com.letsmoe.otter.ui.yearinotter

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.letsmoe.otter.ui.theme.OtterColors
import java.util.Locale
import kotlin.math.roundToInt

/** Draws a poster; swapped in previews and tests that have no network. */
fun interface YearPosterContent {
    @Composable
    fun Poster(url: String?, modifier: Modifier)
}

private val coilPosters = YearPosterContent { url, modifier ->
    AsyncImage(url, contentDescription = null, contentScale = ContentScale.Crop, modifier = modifier)
}

val LocalYearPosters = compositionLocalOf { coilPosters }

/** Room a card leaves for what the screen draws over it (system bars, the story bar, the Share button). */
val LocalYearCardInsets = compositionLocalOf { PaddingValues(0.dp) }

private val posterShape = RoundedCornerShape(16.dp)
private val mutedText = Color.White.copy(alpha = 0.72f)

/** One card, filling the space it is given (a phone screen, or the shared image). */
@Composable
fun YearCardView(card: YearCard, modifier: Modifier = Modifier) {
    when (card) {
        is YearCard.Intro -> IntroCard(card, modifier)
        is YearCard.Hours -> HoursCard(card, modifier)
        is YearCard.Finished -> FinishedCard(card, modifier)
        is YearCard.TopSeries -> TopSeriesCard(card, modifier)
        is YearCard.Genres -> GenresCard(card, modifier)
        is YearCard.Binge -> BingeCard(card, modifier)
        is YearCard.Rewatched -> RewatchedCard(card, modifier)
        is YearCard.FirstAndLast -> FirstAndLastCard(card, modifier)
        is YearCard.Quiet -> QuietCard(card, modifier)
    }
}

@Composable
private fun IntroCard(card: YearCard.Intro, modifier: Modifier) {
    CardFrame(OtterColors.accent, "Your year in Otter", modifier) {
        Text(card.year.toString(), fontSize = 104.sp, lineHeight = 104.sp, fontWeight = FontWeight.Black)
        Text("${card.profileName}, here's your year in films and shows.", fontSize = 22.sp, lineHeight = 30.sp, color = mutedText)
        Text("Swipe to see it", fontSize = 14.sp, color = OtterColors.accent, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun HoursCard(card: YearCard.Hours, modifier: Modifier) {
    CardFrame(OtterColors.accent, "Time watched", modifier) {
        Text(hoursHeadline(card.hours), fontSize = 120.sp, lineHeight = 120.sp, fontWeight = FontWeight.Black)
        Text("hours of Otter", fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Text(hoursCaption(card.hours), fontSize = 20.sp, lineHeight = 28.sp, color = mutedText)
    }
}

@Composable
private fun FinishedCard(card: YearCard.Finished, modifier: Modifier) {
    CardFrame(OtterColors.success, "Finished", modifier) {
        BigStat(card.movies.toString(), if (card.movies == 1) "film" else "films")
        BigStat(card.episodes.toString(), if (card.episodes == 1) "episode" else "episodes")
        Text(
            "across ${finishedLine(card.series, "series", "different series")}.",
            fontSize = 20.sp,
            color = mutedText,
        )
    }
}

@Composable
private fun BigStat(number: String, label: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.Bottom) {
        Text(number, fontSize = 76.sp, lineHeight = 80.sp, fontWeight = FontWeight.Black)
        Text(label, fontSize = 26.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 10.dp))
    }
}

@Composable
private fun TopSeriesCard(card: YearCard.TopSeries, modifier: Modifier) {
    val series = card.series
    CardFrame(OtterColors.warning, "Most watched series", modifier) {
        PosterWithTitle(series.title)
        Text(
            "${hoursHeadline(series.hours)} hours over ${finishedLine(series.episodes, "episode", "episodes")}.",
            fontSize = 20.sp,
            lineHeight = 28.sp,
            color = mutedText,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun PosterWithTitle(title: YearTitle) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Poster(title, Modifier.width(170.dp))
        Text(title.name, fontSize = 32.sp, lineHeight = 36.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center, maxLines = 3, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun Poster(title: YearTitle, modifier: Modifier) {
    val posters = LocalYearPosters.current
    Box(modifier.aspectRatio(2f / 3f).shadow(18.dp, posterShape).clip(posterShape).background(Color.White.copy(alpha = 0.08f))) {
        posters.Poster(title.posterUrl, Modifier.fillMaxSize())
    }
}

@Composable
private fun GenresCard(card: YearCard.Genres, modifier: Modifier) {
    val strongest = card.genres.maxOf { genre -> genre.share }
    CardFrame(Color(0xFFF472B6), "Your genres", modifier) {
        Text(card.genres.first().name, fontSize = 56.sp, lineHeight = 60.sp, fontWeight = FontWeight.Black)
        Text("was your thing this year.", fontSize = 20.sp, color = mutedText)
        Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            for (genre in card.genres) {
                GenreBar(genre, strongest)
            }
        }
    }
}

@Composable
private fun GenreBar(genre: YearGenre, strongest: Double) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(genre.name, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${(genre.share * 100).roundToInt()}%", fontSize = 15.sp, color = mutedText)
        }
        Box(Modifier.fillMaxWidth().height(8.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.12f))) {
            Box(Modifier.fillMaxHeight().fillMaxWidth((genre.share / strongest).toFloat()).background(Color(0xFFF472B6)))
        }
    }
}

@Composable
private fun BingeCard(card: YearCard.Binge, modifier: Modifier) {
    val binge = card.binge
    CardFrame(Color(0xFF60A5FA), "Longest binge", modifier) {
        Text(binge.episodes.toString(), fontSize = 120.sp, lineHeight = 120.sp, fontWeight = FontWeight.Black)
        Text("episodes in one night", fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold)
        Text("${binge.title.name}, the night of ${shortDate(binge.date)}.", fontSize = 20.sp, lineHeight = 28.sp, color = mutedText)
        Poster(binge.title, Modifier.width(110.dp))
    }
}

@Composable
private fun RewatchedCard(card: YearCard.Rewatched, modifier: Modifier) {
    val again = card.rewatched
    CardFrame(OtterColors.accent, "Watched again", modifier) {
        PosterWithTitle(again.title)
        Text(rewatchLine(again), fontSize = 20.sp, lineHeight = 28.sp, color = mutedText, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    }
}

/** What was watched again: a film, or some episodes of a series. */
fun rewatchLine(again: YearReview.Rewatched): String {
    if (!again.title.isSeries) {
        return "You came back to it. Some things are worth a second time."
    }
    return "You went back to ${finishedLine(again.count, "episode", "episodes")} of it."
}

@Composable
private fun FirstAndLastCard(card: YearCard.FirstAndLast, modifier: Modifier) {
    CardFrame(OtterColors.success, "Bookends", modifier) {
        Moment("The year began with", card.first)
        Moment("and ended with", card.last)
    }
}

@Composable
private fun Moment(label: String, moment: YearReview.Moment) {
    Row(horizontalArrangement = Arrangement.spacedBy(18.dp), verticalAlignment = Alignment.CenterVertically) {
        Poster(moment.title, Modifier.width(96.dp))
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, fontSize = 15.sp, color = mutedText)
            Text(moment.title.name, fontSize = 26.sp, lineHeight = 30.sp, fontWeight = FontWeight.Black, maxLines = 3, overflow = TextOverflow.Ellipsis)
            Text(shortDate(moment.date), fontSize = 15.sp, color = mutedText)
        }
    }
}

@Composable
private fun QuietCard(card: YearCard.Quiet, modifier: Modifier) {
    CardFrame(OtterColors.accent, "Your year in Otter", modifier) {
        Text(card.year.toString(), fontSize = 104.sp, lineHeight = 104.sp, fontWeight = FontWeight.Black)
        Text("Nothing watched in Otter this year yet. Start something and come back.", fontSize = 22.sp, lineHeight = 30.sp, color = mutedText)
    }
}

/** The look all cards share: a tinted gradient, the kicker on top, the content in the middle and the wordmark below. */
@Composable
private fun CardFrame(accent: Color, kicker: String, modifier: Modifier, content: @Composable ColumnScope.() -> Unit) {
    val top = lerp(Color.Black, accent, 0.4f)
    val insets = LocalYearCardInsets.current
    Box(modifier.background(Brush.verticalGradient(listOf(top, Color.Black))).padding(insets).padding(horizontal = 28.dp, vertical = 36.dp)) {
        Text(
            kicker.uppercase(Locale.getDefault()),
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 2.5.sp,
            color = accent,
            modifier = Modifier.align(Alignment.TopStart),
        )
        Column(Modifier.align(Alignment.Center).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp), content = content)
        Wordmark(Modifier.align(Alignment.BottomStart))
    }
}

@Composable
private fun Wordmark(modifier: Modifier) {
    Text("otter", fontSize = 18.sp, fontWeight = FontWeight.Black, color = Color.White.copy(alpha = 0.5f), modifier = modifier)
}
