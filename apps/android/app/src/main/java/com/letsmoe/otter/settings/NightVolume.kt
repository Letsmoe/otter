package com.letsmoe.otter.settings

/** A TV's volume runs from 0 to this. */
const val TELEVISION_VOLUME_RANGE = 100

const val DEFAULT_NIGHT_VOLUME_LIMIT = 30
const val MINIMUM_NIGHT_VOLUME_LIMIT = 5

object NightVolume {

    /** The loudest volume the TV may have: the [limit] during the night, anything otherwise. */
    fun maximum(limit: Int?, pastBedtime: Boolean): Int {
        if (limit == null || !pastBedtime) {
            return TELEVISION_VOLUME_RANGE
        }
        return limit
    }

    /** [requested] brought into 0 until [maximum]. */
    fun capped(requested: Int, maximum: Int): Int {
        return requested.coerceIn(0, maximum)
    }

    fun exceeds(volume: Int, maximum: Int): Boolean {
        return volume > maximum
    }
}
