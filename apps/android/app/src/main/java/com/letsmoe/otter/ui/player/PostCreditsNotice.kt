package com.letsmoe.otter.ui.player

/** What the viewer is told while the credits of a film with a scene after them roll. */
const val POST_CREDITS_LABEL = "Scene after the credits"

// Films carry no timed credits; they are guessed from the end of the file.
private const val CREDITS_FRACTION = 0.05
private const val MIN_CREDITS_SECONDS = 240.0
private const val MAX_CREDITS_SECONDS = 540.0
private const val NOTICE_SECONDS = 20.0
// Shorter files are not films with credits worth a notice.
private const val MIN_FILM_SECONDS = 20 * 60.0

/** When the notice shows on the film's timeline: from the guessed start of the credits for [NOTICE_SECONDS]. */
data class PostCreditsNotice(val startSeconds: Double, val endSeconds: Double) {

    fun isShownAt(positionSeconds: Double): Boolean {
        return positionSeconds >= startSeconds && positionSeconds < endSeconds
    }
}

/** The notice for a film of [durationSeconds]; null while its length is unknown or too short. */
fun postCreditsNoticeFor(durationSeconds: Double): PostCreditsNotice? {
    if (durationSeconds.isNaN() || durationSeconds.isInfinite() || durationSeconds < MIN_FILM_SECONDS) {
        return null
    }
    val creditsSeconds = (durationSeconds * CREDITS_FRACTION).coerceIn(MIN_CREDITS_SECONDS, MAX_CREDITS_SECONDS)
    val startSeconds = durationSeconds - creditsSeconds
    return PostCreditsNotice(startSeconds, startSeconds + NOTICE_SECONDS)
}

/** Whether the phone shows the notice at [positionSeconds] of a film that has a scene after its credits. */
fun showsPostCreditsNotice(hasPostCreditsScene: Boolean, positionSeconds: Double, durationSeconds: Double): Boolean {
    if (!hasPostCreditsScene) {
        return false
    }
    val notice = postCreditsNoticeFor(durationSeconds) ?: return false
    return notice.isShownAt(positionSeconds)
}
