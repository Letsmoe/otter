package com.letsmoe.otter.ui.yearinotter

import com.letsmoe.otter.api.posterUrl
import com.letsmoe.otter.graphql.YearInOtterQuery
import com.letsmoe.otter.graphql.fragment.TitleCard
import com.letsmoe.otter.graphql.type.MediaType
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/** A title a highlight points at. */
data class YearTitle(val name: String, val posterUrl: String?, val isSeries: Boolean)

data class YearGenre(val name: String, val hours: Double, val share: Double)

/** The profile's calendar year of watching, as the backend counted it. */
data class YearReview(
    val year: Int,
    val hasActivity: Boolean,
    val hours: Double,
    val moviesFinished: Int,
    val episodesFinished: Int,
    val seriesWatched: Int,
    val topSeries: TopSeries?,
    val topGenres: List<YearGenre>,
    val binge: Binge?,
    val rewatched: Rewatched?,
    val first: Moment?,
    val last: Moment?,
) {
    data class TopSeries(val title: YearTitle, val hours: Double, val episodes: Int)

    data class Binge(val title: YearTitle, val episodes: Int, val date: String)

    data class Rewatched(val title: YearTitle, val count: Int)

    data class Moment(val title: YearTitle, val date: String)
}

fun YearInOtterQuery.YearInReview.toReview(): YearReview = YearReview(
    year = year,
    hasActivity = hasActivity,
    hours = hoursWatched,
    moviesFinished = moviesFinished,
    episodesFinished = episodesFinished,
    seriesWatched = seriesWatched,
    topSeries = topSeries?.let { top -> YearReview.TopSeries(top.title.titleCard.toYearTitle(), top.hoursWatched, top.episodesWatched) },
    topGenres = topGenres.map { genre -> YearGenre(genre.genre, genre.hoursWatched, genre.share) },
    binge = longestBinge?.let { binge -> YearReview.Binge(binge.title.titleCard.toYearTitle(), binge.episodes, binge.date) },
    rewatched = mostRewatched?.let { again -> YearReview.Rewatched(again.title.titleCard.toYearTitle(), again.count) },
    first = firstTitle?.let { moment -> YearReview.Moment(moment.title.titleCard.toYearTitle(), moment.date) },
    last = lastTitle?.let { moment -> YearReview.Moment(moment.title.titleCard.toYearTitle(), moment.date) },
)

private fun TitleCard.toYearTitle(): YearTitle =
    YearTitle(title, posterUrl(posterPath), mediaType == MediaType.SERIES)

/** One full-screen card of the review, in swipe order. */
sealed interface YearCard {
    data class Intro(val year: Int, val profileName: String) : YearCard

    data class Hours(val hours: Double) : YearCard

    data class Finished(val movies: Int, val episodes: Int, val series: Int) : YearCard

    data class TopSeries(val series: YearReview.TopSeries) : YearCard

    data class Genres(val genres: List<YearGenre>) : YearCard

    data class Binge(val binge: YearReview.Binge) : YearCard

    data class Rewatched(val rewatched: YearReview.Rewatched) : YearCard

    data class FirstAndLast(val first: YearReview.Moment, val last: YearReview.Moment) : YearCard

    /** A year without watching. */
    data class Quiet(val year: Int) : YearCard
}

/** The cards a review has something to say for, introduction first. */
fun yearCards(review: YearReview, profileName: String): List<YearCard> {
    if (!review.hasActivity) {
        return listOf(YearCard.Quiet(review.year))
    }
    val cards = mutableListOf<YearCard>(YearCard.Intro(review.year, profileName), YearCard.Hours(review.hours))
    cards.add(YearCard.Finished(review.moviesFinished, review.episodesFinished, review.seriesWatched))
    review.topSeries?.let { series -> cards.add(YearCard.TopSeries(series)) }
    if (review.topGenres.isNotEmpty()) {
        cards.add(YearCard.Genres(review.topGenres))
    }
    review.binge?.let { binge -> cards.add(YearCard.Binge(binge)) }
    review.rewatched?.let { again -> cards.add(YearCard.Rewatched(again)) }
    if (review.first != null && review.last != null) {
        cards.add(YearCard.FirstAndLast(review.first, review.last))
    }
    return cards
}

private const val HOURS_PER_DAY = 24.0
private const val HOURS_PER_FILM = 2.0

/** "128" for the big number; one decimal below ten hours so a short year still reads right. */
fun hoursHeadline(hours: Double): String {
    if (hours < 10) {
        return String.format(Locale.US, "%.1f", hours)
    }
    return hours.roundToInt().toString()
}

/** What the hours add up to: days back to back, or films' worth. */
fun hoursCaption(hours: Double): String {
    if (hours >= HOURS_PER_DAY * 2) {
        return "That's about ${(hours / HOURS_PER_DAY).roundToInt()} days, back to back."
    }
    val films = (hours / HOURS_PER_FILM).roundToInt()
    if (films <= 1) {
        return "A good start."
    }
    return "That's about $films films' worth."
}

fun finishedLine(count: Int, singular: String, plural: String): String {
    if (count == 1) {
        return "1 $singular"
    }
    return "$count $plural"
}

/** "12 May" for "2026-05-12"; the text itself when it isn't a date. */
fun shortDate(isoDate: String, locale: Locale = Locale.getDefault()): String {
    try {
        return LocalDate.parse(isoDate).format(DateTimeFormatter.ofPattern("d MMMM", locale))
    } catch (error: java.time.format.DateTimeParseException) {
        return isoDate
    }
}

private const val DECEMBER = 12
private const val JANUARY = 1

/** December and January are when the profile is offered its year. */
fun isReviewSeason(today: LocalDate): Boolean = today.monthValue == DECEMBER || today.monthValue == JANUARY

/** The year to look back on: in January the one that just ended, otherwise this one. */
fun reviewYear(today: LocalDate): Int {
    if (today.monthValue == JANUARY) {
        return today.year - 1
    }
    return today.year
}
