package com.letsmoe.otter.ui.watchorder

import kotlin.math.roundToInt

/** The guide's main button: start the first film, continue with the next one, or nothing when all out is watched. */
fun continueLabel(guide: WatchOrderGuide): String? {
    val next = guide.next
    if (next == null) {
        return null
    }
    if (!guide.isStarted) {
        return "Start with ${next.title}"
    }
    return "Continue with ${next.title}"
}

/** "3 of 8 films watched". */
fun progressLabel(guide: WatchOrderGuide): String = "${guide.watchedCount} of ${guide.parts.size} films watched"

/** Share (0..1) of the franchise's films that are watched. */
fun watchedFraction(guide: WatchOrderGuide): Float {
    if (guide.parts.isEmpty()) {
        return 0f
    }
    return guide.watchedCount.toFloat() / guide.parts.size
}

/** What a film's row says about the profile's progress. */
fun statusLabel(part: GuidePart): String {
    if (part.status == PartStatus.WATCHED) {
        return "Watched"
    }
    if (part.status == PartStatus.IN_PROGRESS) {
        return "In progress · ${(part.progress * 100).roundToInt()}% watched"
    }
    if (!part.released) {
        return "Coming soon"
    }
    return "Not watched yet"
}

/** "Up next" for the film the main button continues with; "2003" style year otherwise. */
fun partFacts(part: GuidePart, isNext: Boolean): String {
    val facts = mutableListOf<String>()
    if (part.year != null) {
        facts.add(part.year.toString())
    }
    if (isNext) {
        facts.add("Up next")
    }
    return facts.joinToString(" · ")
}
