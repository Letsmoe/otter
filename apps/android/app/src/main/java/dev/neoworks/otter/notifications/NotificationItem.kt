package dev.neoworks.otter.notifications

import dev.neoworks.otter.api.tmdbImageUrl
import dev.neoworks.otter.graphql.fragment.NotificationFields
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/** One news item from the backend's watchlist watcher. */
data class NotificationItem(
    val id: String,
    val heading: String,
    val body: String?,
    val createdAt: String,
    val read: Boolean,
    val titleId: String,
    val titleName: String,
    val imageUrl: String?,
    /** Set when the notification is about one episode. */
    val seasonNumber: Int?,
    val episodeId: String?,
)

private const val IMAGE_SIZE = "w780"
private const val RELATIVE_DAYS = 7L

// Episodes show their still, falling back to the series' backdrop.
fun NotificationFields.toNotificationItem(): NotificationItem {
    val titleImage = tmdbImageUrl(title.backdropPath, IMAGE_SIZE)
    var imageUrl = titleImage
    val episodeFields = episode
    if (episodeFields != null) {
        imageUrl = tmdbImageUrl(episodeFields.stillPath, IMAGE_SIZE) ?: titleImage
    }
    return NotificationItem(
        id = id,
        heading = heading,
        body = body,
        createdAt = createdAt,
        read = read,
        titleId = title.id,
        titleName = title.title,
        imageUrl = imageUrl,
        seasonNumber = episodeFields?.seasonNumber,
        episodeId = episodeFields?.id,
    )
}

/** "Today", "Yesterday", "3 days ago" or a date, for a notification's age. */
fun notificationAge(createdAt: String, today: LocalDate = LocalDate.now()): String {
    val created = parseCreatedDate(createdAt) ?: return ""
    val days = ChronoUnit.DAYS.between(created, today)
    if (days <= 0) {
        return "Today"
    }
    if (days == 1L) {
        return "Yesterday"
    }
    if (days < RELATIVE_DAYS) {
        return "$days days ago"
    }
    return created.format(DateTimeFormatter.ofPattern("d MMM"))
}

private fun parseCreatedDate(createdAt: String): LocalDate? {
    return try {
        OffsetDateTime.parse(createdAt).atZoneSameInstant(ZoneId.systemDefault()).toLocalDate()
    } catch (error: Exception) {
        parseWithoutOffset(createdAt)
    }
}

// SQLite-style "2025-01-02 10:00:00" stamps carry no offset and are UTC.
private fun parseWithoutOffset(createdAt: String): LocalDate? {
    return try {
        val local = LocalDateTime.parse(createdAt.replace(' ', 'T'))
        Instant.from(local.atZone(ZoneId.of("UTC"))).atZone(ZoneId.systemDefault()).toLocalDate()
    } catch (error: Exception) {
        null
    }
}
