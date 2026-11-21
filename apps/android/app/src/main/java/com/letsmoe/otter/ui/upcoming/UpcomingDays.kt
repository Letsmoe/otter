package com.letsmoe.otter.ui.upcoming

import com.letsmoe.otter.api.tmdbImageUrl
import com.letsmoe.otter.graphql.UpcomingEpisodesQuery
import com.letsmoe.otter.ui.media.RailItem
import com.letsmoe.otter.ui.media.toRailItem
import com.letsmoe.otter.ui.media.withEpisode
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

/** How far ahead the screen looks. */
const val UPCOMING_DAYS = 28

/** One episode that has not aired yet; [item] opens its series on that episode. */
data class UpcomingEpisode(val item: RailItem, val airDate: LocalDate, val imageUrl: String?)

/** The episodes airing on one day: "Today", "Tomorrow" or the weekday, with the date beside it. */
data class UpcomingDay(val heading: String, val dateLabel: String, val episodes: List<UpcomingEpisode>)

private const val STILL_SIZE = "w300"
private const val DATE_LENGTH = 10

fun UpcomingEpisodesQuery.UpcomingEpisode.toUpcomingEpisode(): UpcomingEpisode? {
    val airDate = parseAirDate(episode.airDate)
    if (airDate == null) {
        return null
    }
    val seriesItem = series.titleCard.toRailItem()
    var imageUrl = tmdbImageUrl(episode.stillPath, STILL_SIZE)
    if (imageUrl == null) {
        imageUrl = seriesItem.backdropUrl
    }
    return UpcomingEpisode(seriesItem.withEpisode(episode.episodeRef), airDate, imageUrl)
}

private fun parseAirDate(airDate: String?): LocalDate? {
    if (airDate.isNullOrEmpty()) {
        return null
    }
    return try {
        LocalDate.parse(airDate.take(DATE_LENGTH))
    } catch (error: DateTimeParseException) {
        null
    }
}

/** Groups [episodes] by air date, soonest day first; episodes of a day keep their order. */
fun groupByDay(episodes: List<UpcomingEpisode>, today: LocalDate, locale: Locale = Locale.getDefault()): List<UpcomingDay> {
    return episodes
        .filter { episode -> !episode.airDate.isBefore(today) }
        .groupBy { episode -> episode.airDate }
        .toSortedMap()
        .map { (date, dayEpisodes) -> UpcomingDay(dayHeading(date, today, locale), dateLabel(date, locale), dayEpisodes) }
}

fun dayHeading(date: LocalDate, today: LocalDate, locale: Locale = Locale.getDefault()): String {
    if (date == today) {
        return "Today"
    }
    if (date == today.plusDays(1)) {
        return "Tomorrow"
    }
    return date.format(DateTimeFormatter.ofPattern("EEEE", locale))
}

private fun dateLabel(date: LocalDate, locale: Locale): String = date.format(DateTimeFormatter.ofPattern("d MMM", locale))
