package com.letsmoe.otter.cast.overlay

import android.graphics.Bitmap
import kotlin.math.ceil
import kotlin.math.floor

/** A caption on the title's timeline. */
data class CaptionCue(val startSeconds: Double, val endSeconds: Double, val text: String)

/** An intro or recap: the picture tells the viewer to skip it on the phone. */
data class SkipHint(val label: String, val startSeconds: Double, val endSeconds: Double)

/** The next episode's card, from [startSeconds] (the credits) for [durationSeconds], counting down. */
data class UpNextHint(val episodeLabel: String, val startSeconds: Double, val durationSeconds: Double, val still: Bitmap?)

/** Everything drawn into the TV's picture, on the title's timeline. */
data class OverlayPlan(
    val captions: List<CaptionCue> = emptyList(),
    val skips: List<SkipHint> = emptyList(),
    val upNext: UpNextHint? = null,
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
) {
    val isEmpty: Boolean
        get() = caption == null && skipLabel == null && upNextSecondsLeft == null
}

fun OverlayPlan.frameAt(seconds: Double): OverlayFrame {
    return OverlayFrame(captionAt(seconds), skipLabelAt(seconds), upNextSecondsLeftAt(seconds), upNextProgressAt(seconds))
}

// Overlapping cues (two speakers) are shown together, earliest on top.
private fun OverlayPlan.captionAt(seconds: Double): String? {
    val showing = captions.filter { cue -> seconds >= cue.startSeconds && seconds < cue.endSeconds }
    if (showing.isEmpty()) {
        return null
    }
    return showing.joinToString("\n") { cue -> cue.text }
}

private fun OverlayPlan.skipLabelAt(seconds: Double): String? {
    val skip = skips.firstOrNull { hint -> seconds >= hint.startSeconds && seconds < hint.endSeconds }
    return skip?.label
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
