package com.letsmoe.otter.settings

/** How long after the bedtime the evening still counts as the night, in minutes. */
const val BEDTIME_NIGHT_MINUTES = 8 * 60

/** Bedtime as minutes after midnight, e.g. 23:00 is 1380. */
const val DEFAULT_BEDTIME_MINUTES = 23 * 60

private const val MINUTES_PER_DAY = 24 * 60

object Bedtime {

    /**
     * Whether [nowMinutes] lies in the night that starts at [bedtimeMinutes]. The night wraps past
     * midnight: with a bedtime of 23:00, 02:00 still counts, 08:00 doesn't.
     */
    fun hasStarted(bedtimeMinutes: Int, nowMinutes: Int): Boolean {
        val minutesSinceBedtime = Math.floorMod(nowMinutes - bedtimeMinutes, MINUTES_PER_DAY)
        return minutesSinceBedtime < BEDTIME_NIGHT_MINUTES
    }

    /** Minutes from [nowMinutes] until the next bedtime; null once the night has started. */
    fun minutesUntil(bedtimeMinutes: Int, nowMinutes: Int): Int? {
        if (hasStarted(bedtimeMinutes, nowMinutes)) {
            return null
        }
        return Math.floorMod(bedtimeMinutes - nowMinutes, MINUTES_PER_DAY)
    }
}
