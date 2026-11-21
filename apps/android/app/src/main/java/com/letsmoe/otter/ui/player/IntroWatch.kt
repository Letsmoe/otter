package com.letsmoe.otter.ui.player

private const val INTRO_KIND = "intro"

// Position updates come every 250 ms; a bigger step is a seek, not watching.
private const val MAX_WATCHED_STEP_SECONDS = 1.5

/**
 * Tells whether the viewer skipped an intro by hand or watched it through, once per intro and
 * episode. Automatic skips and seeks over the intro say nothing.
 */
class IntroWatch(private val onOutcome: (IntroOutcome) -> Unit) {

    private var previousSeconds: Double? = null
    private val watching = mutableSetOf<SkipJump>()
    private val decided = mutableSetOf<SkipJump>()

    fun reset() {
        previousSeconds = null
        watching.clear()
        decided.clear()
    }

    fun follow(jumps: List<SkipJump>, positionSeconds: Double, playing: Boolean) {
        val previous = previousSeconds
        previousSeconds = positionSeconds
        if (!playing) {
            return
        }
        for (jump in jumps) {
            followIntro(jump, previous, positionSeconds)
        }
    }

    fun skippedByHand(jump: SkipJump) {
        if (!isUndecidedIntro(jump)) {
            return
        }
        decided.add(jump)
        onOutcome(IntroOutcome.SKIPPED_BY_HAND)
    }

    private fun followIntro(jump: SkipJump, previous: Double?, positionSeconds: Double) {
        if (!isUndecidedIntro(jump)) {
            return
        }
        if (positionSeconds >= jump.startSeconds && positionSeconds < jump.endSeconds) {
            watching.add(jump)
            return
        }
        if (jump in watching && playedPast(jump, previous, positionSeconds)) {
            decided.add(jump)
            onOutcome(IntroOutcome.WATCHED_THROUGH)
        }
    }

    private fun isUndecidedIntro(jump: SkipJump): Boolean {
        return jump.kind == INTRO_KIND && jump !in decided
    }

    private fun playedPast(jump: SkipJump, previous: Double?, positionSeconds: Double): Boolean {
        if (previous == null || previous >= jump.endSeconds) {
            return false
        }
        return positionSeconds >= jump.endSeconds && positionSeconds - previous <= MAX_WATCHED_STEP_SECONDS
    }
}
