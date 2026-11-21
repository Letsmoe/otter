package com.letsmoe.otter.ui.player

/** What the TV's picture says shortly before a film or a series' last episode ends. */
const val RATING_REMINDER_LABEL = "Rate it on your phone"

// The reminder shows this long before the end of the file.
private const val REMINDER_SECONDS = 10.0

/** The part of a film's timeline where the reminder shows: its last [REMINDER_SECONDS]. */
data class RatingReminderWindow(val startSeconds: Double, val endSeconds: Double)

/** The window for a title of [durationSeconds]; null while its length is unknown. */
fun ratingReminderWindowFor(durationSeconds: Double): RatingReminderWindow? {
    if (durationSeconds.isNaN() || durationSeconds.isInfinite() || durationSeconds <= REMINDER_SECONDS) {
        return null
    }
    return RatingReminderWindow(durationSeconds - REMINDER_SECONDS, durationSeconds)
}

/**
 * Reminds to rate a film, or a series' last episode, unless the profile rated it already: the phone
 * shows the rating buttons for the last seconds and after the end, the TV's picture says to rate on
 * the phone.
 */
class RatingReminder {

    private var rateable = false
    private var unrated = false

    fun reset() {
        rateable = false
        unrated = false
    }

    /** [rateable]: a film or a series' last episode; [unrated]: the profile hasn't rated it. */
    fun open(rateable: Boolean, unrated: Boolean) {
        this.rateable = rateable
        this.unrated = unrated
    }

    /** Whether this title is to be rated: the TV's picture says so ahead of the end. */
    val asksForRating: Boolean
        get() = rateable && unrated

    /** Whether the phone shows the rating buttons now. */
    fun shows(positionSeconds: Double, durationSeconds: Double, ended: Boolean): Boolean {
        if (!asksForRating) {
            return false
        }
        if (ended) {
            return true
        }
        val window = ratingReminderWindowFor(durationSeconds) ?: return false
        return positionSeconds >= window.startSeconds
    }
}
