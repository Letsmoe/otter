package com.letsmoe.otter.cast.overlay

private val timingLine = Regex("""(\d+):(\d{2}):(\d{2})[,.](\d{1,3})\s*-->\s*(\d+):(\d{2}):(\d{2})[,.](\d{1,3})""")
// Markup the backend leaves in: <i>, <font …>, {\an8} and the like. The picture shows plain text.
private val markup = Regex("""<[^>]*>|\{\\[^}]*\}""")

/** The cues of an SRT file; blocks without a timing line are skipped. */
fun parseSrt(text: String): List<CaptionCue> {
    val blocks = text.replace("\r\n", "\n").replace('\r', '\n').split(Regex("""\n\s*\n"""))
    return blocks.mapNotNull(::cueOf).sortedBy { cue -> cue.startSeconds }
}

private fun cueOf(block: String): CaptionCue? {
    val lines = block.trim().lines()
    val timingIndex = lines.indexOfFirst { line -> timingLine.containsMatchIn(line) }
    if (timingIndex < 0) {
        return null
    }
    val timing = timingLine.find(lines[timingIndex]) ?: return null
    val text = cueText(lines.drop(timingIndex + 1))
    if (text.isEmpty()) {
        return null
    }
    val values = timing.groupValues
    return CaptionCue(secondsOf(values, 1), secondsOf(values, 5), text)
}

private fun cueText(lines: List<String>): String {
    return lines.map { line -> line.replace(markup, "").trim() }.filter { line -> line.isNotEmpty() }.joinToString("\n")
}

// Reads hours, minutes, seconds and milliseconds from four groups starting at [first].
private fun secondsOf(values: List<String>, first: Int): Double {
    val hours = values[first].toDouble()
    val minutes = values[first + 1].toDouble()
    val seconds = values[first + 2].toDouble()
    val milliseconds = values[first + 3].padEnd(3, '0').toDouble()
    return hours * 3600 + minutes * 60 + seconds + milliseconds / 1000
}
