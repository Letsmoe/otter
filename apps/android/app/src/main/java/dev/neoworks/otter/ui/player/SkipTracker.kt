package dev.neoworks.otter.ui.player

// Skippable with a jump; credits lead to the next episode instead.
private val JUMP_KINDS = setOf("intro", "recap")
private const val CREDITS_KIND = "credits"

// Closer than this to a segment's end, a jump isn't worth it.
private const val MIN_JUMP_SECONDS = 2.0

/** An intro or recap playback can jump over, with its button label. */
data class SkipJump(val kind: String, val startSeconds: Double, val endSeconds: Double) {
    val label: String
        get() = when (kind) {
            "recap" -> "Skip Recap"
            else -> "Skip Intro"
        }
}

/**
 * Knows the playing title's intro, recap and credits. Each intro and recap is
 * jumped over on its own once, so seeking back into one plays it, with a
 * button to skip it then.
 */
class SkipTracker {

    private var jumps = emptyList<SkipJump>()
    private val autoSkipped = mutableSetOf<SkipJump>()

    /** Where the credits start; null when nobody timed them. */
    var creditsStartSeconds: Double? = null
        private set

    fun reset(segments: List<SkipSegment>) {
        jumps = segments.mapNotNull(::jumpOf)
        autoSkipped.clear()
        creditsStartSeconds = segments.firstOrNull { segment -> segment.kind == CREDITS_KIND }?.startSeconds
    }

    /** Where to continue when [positionSeconds] entered a jump not taken yet; null otherwise. */
    fun autoSkipTarget(positionSeconds: Double): Double? {
        val jump = jumpAt(positionSeconds) ?: return null
        if (jump in autoSkipped) {
            return null
        }
        autoSkipped.add(jump)
        return jump.endSeconds
    }

    /** The intro or recap playing at [positionSeconds], for its skip button. */
    fun jumpAt(positionSeconds: Double): SkipJump? {
        return jumps.firstOrNull { jump -> positionSeconds >= jump.startSeconds && positionSeconds < jump.endSeconds - MIN_JUMP_SECONDS }
    }

    fun isInCredits(positionSeconds: Double): Boolean {
        val creditsStart = creditsStartSeconds ?: return false
        return positionSeconds >= creditsStart
    }
}

private fun jumpOf(segment: SkipSegment): SkipJump? {
    val end = segment.endSeconds ?: return null
    if (segment.kind !in JUMP_KINDS) {
        return null
    }
    return SkipJump(segment.kind, segment.startSeconds, end)
}
