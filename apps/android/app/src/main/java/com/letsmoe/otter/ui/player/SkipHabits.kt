package com.letsmoe.otter.ui.player

import com.letsmoe.otter.settings.AutoSkipIntro

// Intros skipped by hand this many times in a row are skipped on their own from then on.
private const val SKIPS_TO_LEARN = 3
// Watching an intro through this many times in a row ends that.
private const val WATCHES_TO_FORGET = 2
// A recap is skipped while the episode before it finished less than this long ago.
private const val BACK_TO_BACK_MILLISECONDS = 24 * 60 * 60 * 1000L

private const val RECAP_KIND = "recap"

/** What the viewer did with an intro. */
enum class IntroOutcome { SKIPPED_BY_HAND, WATCHED_THROUGH }

/** How a series' intro has been treated lately, and whether it is skipped on its own now. */
data class IntroHabit(val skipsInARow: Int = 0, val watchesInARow: Int = 0, val autoSkips: Boolean = false) {

    fun after(outcome: IntroOutcome): IntroHabit {
        if (outcome == IntroOutcome.SKIPPED_BY_HAND) {
            return afterSkip()
        }
        return afterWatch()
    }

    private fun afterSkip(): IntroHabit {
        val skips = skipsInARow + 1
        return IntroHabit(skips, 0, autoSkips || skips >= SKIPS_TO_LEARN)
    }

    private fun afterWatch(): IntroHabit {
        val watches = watchesInARow + 1
        return IntroHabit(0, watches, autoSkips && watches < WATCHES_TO_FORGET)
    }

    fun encode(): String = "$skipsInARow,$watchesInARow,$autoSkips"

    companion object {
        /** The habit [encode] wrote; a fresh one for anything else. */
        fun decode(stored: String?): IntroHabit {
            val parts = stored?.split(",") ?: return IntroHabit()
            if (parts.size != 3) {
                return IntroHabit()
            }
            val skips = parts[0].toIntOrNull() ?: return IntroHabit()
            val watches = parts[1].toIntOrNull() ?: return IntroHabit()
            return IntroHabit(skips, watches, parts[2] == "true")
        }
    }
}

/** The episode of a series the profile finished last, and when. */
data class FinishedEpisode(val episodeId: String, val finishedAtMillis: Long) {

    fun encode(): String = "$finishedAtMillis,$episodeId"

    companion object {
        fun decode(stored: String?): FinishedEpisode? {
            val parts = stored?.split(",", limit = 2) ?: return null
            if (parts.size != 2) {
                return null
            }
            val finishedAtMillis = parts[0].toLongOrNull() ?: return null
            return FinishedEpisode(parts[1], finishedAtMillis)
        }
    }
}

/** Whether [finished] is the episode right before this one and ended less than a day before [nowMillis]. */
fun isWatchedBackToBack(previousEpisodeId: String?, finished: FinishedEpisode?, nowMillis: Long): Boolean {
    if (previousEpisodeId == null || finished == null || finished.episodeId != previousEpisodeId) {
        return false
    }
    return nowMillis - finished.finishedAtMillis < BACK_TO_BACK_MILLISECONDS
}

/** The episode before [episodeId] in the series' order, seasons included. */
fun List<PlayerEpisode>.episodeBefore(episodeId: String): PlayerEpisode? {
    val index = indexOfFirst { episode -> episode.id == episodeId }
    return getOrNull(index - 1)
}

/**
 * Learns per series what the viewer does with intros and recaps. Intros the viewer skips by hand
 * a few times are skipped on their own, until they watch them again; recaps are skipped while
 * episodes are watched back to back. Used by [AutoSkipIntro.HABITS]; the outcomes are recorded
 * in every mode.
 */
class SkipHabits(private val storage: SkipHabitStorage, private val clockMillis: () -> Long = System::currentTimeMillis) {

    private var seriesKey: String? = null
    private var episodeId = ""
    private var introHabit = IntroHabit()
    private var recapsSkipped = false
    private var finishedRecorded = false
    private val introWatch = IntroWatch(::recordIntroOutcome)

    /** A new title starts; nothing is known about its series until [open]. */
    fun reset() {
        seriesKey = null
        introHabit = IntroHabit()
        recapsSkipped = false
        finishedRecorded = false
        introWatch.reset()
    }

    /** The episode [openedEpisodeId] of series [seriesId] plays for [profileId]; [previousEpisodeId] is the one before it. */
    fun open(profileId: String, seriesId: String?, openedEpisodeId: String, previousEpisodeId: String?) {
        reset()
        if (seriesId == null) {
            return
        }
        val key = "$profileId|$seriesId"
        seriesKey = key
        episodeId = openedEpisodeId
        introHabit = storage.readIntroHabit(key)
        recapsSkipped = isWatchedBackToBack(previousEpisodeId, storage.readFinishedEpisode(key), clockMillis())
    }

    /** Whether [jump] is jumped over without a button press, in the device's [mode]. */
    fun skips(jump: SkipJump, mode: AutoSkipIntro, casting: Boolean): Boolean {
        return when (mode) {
            AutoSkipIntro.OFF -> false
            AutoSkipIntro.TELEVISION -> casting
            AutoSkipIntro.ALWAYS -> true
            AutoSkipIntro.HABITS -> learnedToSkip(jump)
        }
    }

    private fun learnedToSkip(jump: SkipJump): Boolean {
        if (jump.kind == RECAP_KIND) {
            return recapsSkipped
        }
        return introHabit.autoSkips
    }

    /** Called with every position update. */
    fun follow(jumps: List<SkipJump>, positionSeconds: Double, playing: Boolean, inCredits: Boolean) {
        introWatch.follow(jumps, positionSeconds, playing)
        if (inCredits) {
            recordFinished()
        }
    }

    fun skippedByHand(jump: SkipJump) {
        introWatch.skippedByHand(jump)
    }

    fun episodeEnded() {
        recordFinished()
    }

    private fun recordFinished() {
        val key = seriesKey ?: return
        if (finishedRecorded) {
            return
        }
        finishedRecorded = true
        storage.writeFinishedEpisode(key, FinishedEpisode(episodeId, clockMillis()))
    }

    private fun recordIntroOutcome(outcome: IntroOutcome) {
        val key = seriesKey ?: return
        introHabit = introHabit.after(outcome)
        storage.writeIntroHabit(key, introHabit)
    }
}
