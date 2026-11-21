package com.letsmoe.otter.ui.home

import com.letsmoe.otter.settings.Bedtime

/** The fixed time slots "I have X minutes" offers. */
val FIXED_TIME_SLOTS = listOf(20, 45, 90)

/** One choice in the "I have X minutes" sheet; [untilBedtime] marks the one that ends at the bedtime. */
data class TimeSlotChoice(val label: String, val minutes: Int, val untilBedtime: Boolean = false)

/**
 * The choices to offer: the fixed slots, and "Until bedtime" while a bedtime is set and still
 * ahead. [nowMinutes] is the time of day in minutes after midnight.
 */
fun timeSlotChoices(bedtimeMinutes: Int?, nowMinutes: Int): List<TimeSlotChoice> {
    val choices = FIXED_TIME_SLOTS.map { minutes -> TimeSlotChoice("$minutes min", minutes) }
    if (bedtimeMinutes == null) {
        return choices
    }
    val untilBedtime = Bedtime.minutesUntil(bedtimeMinutes, nowMinutes)
    if (untilBedtime == null || untilBedtime == 0) {
        return choices
    }
    return choices + TimeSlotChoice("Until bedtime", untilBedtime, untilBedtime = true)
}
