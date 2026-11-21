package com.letsmoe.otter.cast.overlay

import android.graphics.Bitmap
import kotlin.math.ceil
import kotlin.math.floor

/** A caption on the title's timeline. */
data class CaptionCue(val startSeconds: Double, val endSeconds: Double, val text: String)

/** An intro or recap: the picture tells the viewer to skip it on the phone. */
data class SkipHint(val label: String, val startSeconds: Double, val endSeconds: Double)

/**
 * The next episode's card, from [startSeconds] (the credits) for [durationSeconds], counting down.
 * One that doesn't [countsDown] only says [heading] and [episodeLabel], e.g. that a season's finale has nothing after it.
 */
data class UpNextHint(
    val episodeLabel: String,
    val startSeconds: Double,
    val durationSeconds: Double,
    val still: Bitmap?,
    val heading: String = "Next episode",
    val countsDown: Boolean = true,
)

/**
 * What is playing, shown from [startSeconds] to [endSeconds] when a title
 * starts: "Severance", "S1:E2 · Half Loop", "Episode 2 of 9" and "Ends at 22:41".
 */
data class TitleCard(
    val title: String,
    val detail: String?,
    val startSeconds: Double,
    val endSeconds: Double,
    val endsAtLabel: String? = null,
    val episodeCountLabel: String? = null,
    /** "2 of 3 tonight" for a title from tonight's queue. */
    val tonightLabel: String? = null,
)

/** Everything drawn into the TV's picture, on the title's timeline. [secondCaptions] show smaller, under [captions]. */
data class OverlayPlan(
    val captions: List<CaptionCue> = emptyList(),
    val skips: List<SkipHint> = emptyList(),
    val upNext: UpNextHint? = null,
    val secondCaptions: List<CaptionCue> = emptyList(),
    val titleCard: TitleCard? = null,
    /** Shown during a replay with captions when [captions] are off. */
    val replayCaptions: List<CaptionCue> = emptyList(),
)

// Steps of the card's fill: about one per frame, so it moves smoothly but frames can still repeat.
private const val UP_NEXT_PROGRESS_STEPS = 240

/**
 * What the picture shows at one moment; the same frame twice needs no new
 * drawing. [upNextProgress] (0 to 1) fills the card as its countdown runs.
 */
data class OverlayFrame(
    val caption: String?,
    val skipLabel: String?,
    val upNextSecondsLeft: Int?,
    val upNextProgress: Float? = null,
    val secondCaption: String? = null,
    val titleCard: TitleCard? = null,
) {
    val isEmpty: Boolean
        get() = caption == null && secondCaption == null && skipLabel == null && upNextSecondsLeft == null && titleCard == null
}

/**
 * The frame at [seconds] of the title; captions show [captionDelaySeconds]
 * later than their file says. Until [replayUntilSeconds] a replay shows
 * captions even when they are off.
 */
fun OverlayPlan.frameAt(seconds: Double, captionDelaySeconds: Double = 0.0, replayUntilSeconds: Double? = null): OverlayFrame {
    val captionSeconds = seconds - captionDelaySeconds
    return OverlayFrame(
        caption = captionAt(captionSeconds, isReplaying(seconds, replayUntilSeconds)),
        skipLabel = skipLabelAt(seconds),
        upNextSecondsLeft = upNextSecondsLeftAt(seconds),
        upNextProgress = upNextProgressAt(seconds),
        secondCaption = secondCaptions.textAt(captionSeconds),
        titleCard = titleCardAt(seconds),
    )
}

/** The caption text at [seconds]; overlapping cues (two speakers) are shown together, earliest on top. */
fun List<CaptionCue>.textAt(seconds: Double): String? {
    val showing = filter { cue -> seconds >= cue.startSeconds && seconds < cue.endSeconds }
    if (showing.isEmpty()) {
        return null
    }
    return showing.joinToString("\n") { cue -> cue.text }
}

private fun OverlayPlan.captionAt(captionSeconds: Double, replaying: Boolean): String? {
    val shown = captions.textAt(captionSeconds)
    if (shown != null || !replaying) {
        return shown
    }
    return replayCaptions.textAt(captionSeconds)
}

private fun isReplaying(seconds: Double, replayUntilSeconds: Double?): Boolean {
    if (replayUntilSeconds == null) {
        return false
    }
    return seconds < replayUntilSeconds
}

private fun OverlayPlan.skipLabelAt(seconds: Double): String? {
    val skip = skips.firstOrNull { hint -> seconds >= hint.startSeconds && seconds < hint.endSeconds }
    return skip?.label
}

private fun OverlayPlan.titleCardAt(seconds: Double): TitleCard? {
    val card = titleCard ?: return null
    if (seconds < card.startSeconds || seconds >= card.endSeconds) {
        return null
    }
    return card
}

private fun OverlayPlan.upNextSecondsLeftAt(seconds: Double): Int? {
    val hint = upNext ?: return null
    val endSeconds = hint.startSeconds + hint.durationSeconds
    if (seconds < hint.startSeconds || seconds >= endSeconds) {
        return null
    }
    return ceil(endSeconds - seconds).toInt()
}

private fun OverlayPlan.upNextProgressAt(seconds: Double): Float? {
    val hint = upNext ?: return null
    val elapsed = (seconds - hint.startSeconds) / hint.durationSeconds
    if (elapsed < 0 || elapsed >= 1) {
        return null
    }
    return floor(elapsed * UP_NEXT_PROGRESS_STEPS).toFloat() / UP_NEXT_PROGRESS_STEPS
}
