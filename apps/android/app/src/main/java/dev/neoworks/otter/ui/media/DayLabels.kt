package dev.neoworks.otter.ui.media

import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

private const val DAYS_IN_WEEK = 7
private val shortDate = DateTimeFormatter.ofPattern("d MMM")
private val badgeDate = DateTimeFormatter.ofPattern("MMM d")

/** "Today", "Yesterday", "3 days ago" or a date; mirrors notificationAge in packages/otter-app. */
fun dayLabel(timestamp: String, today: LocalDate = LocalDate.now()): String {
    val day = OffsetDateTime.parse(timestamp).atZoneSameInstant(ZoneId.systemDefault()).toLocalDate()
    val days = ChronoUnit.DAYS.between(day, today)
    if (days <= 0) {
        return "Today"
    }
    if (days == 1L) {
        return "Yesterday"
    }
    if (days < DAYS_IN_WEEK) {
        return "$days days ago"
    }
    return day.format(shortDate)
}

/** A release date in the future as "Feb 11"; null once it has passed. */
fun releaseBadge(releaseDate: String?, today: LocalDate = LocalDate.now()): String? {
    if (releaseDate.isNullOrEmpty()) {
        return null
    }
    val date = LocalDate.parse(releaseDate.take(10))
    if (!date.isAfter(today)) {
        return null
    }
    return date.format(badgeDate)
}

private const val RECENTLY_ADDED_DAYS = 30L

/** "Recently Added" for titles released within the last 30 days; null otherwise. */
fun recentlyAddedLabel(releaseDate: String?, today: LocalDate = LocalDate.now()): String? {
    if (releaseDate.isNullOrEmpty() || releaseDate.length < 10) {
        return null
    }
    val date = LocalDate.parse(releaseDate.take(10))
    val daysAgo = ChronoUnit.DAYS.between(date, today)
    if (daysAgo < 0 || daysAgo > RECENTLY_ADDED_DAYS) {
        return null
    }
    return "Recently Added"
}
