package com.letsmoe.otter.ui.title

// How a title page words its facts, credits and Play button; shared by the phone and the TV.

private const val STARRING_COUNT = 3

/** "2.4 MB/s"; null without a measurement. */
fun formatSpeed(bytesPerSecond: Double?): String? {
    if (bytesPerSecond == null || bytesPerSecond <= 0) {
        return null
    }
    if (bytesPerSecond >= 1_000_000) {
        return "%.1f MB/s".format(bytesPerSecond / 1_000_000)
    }
    if (bytesPerSecond >= 1_000) {
        return "%.0f KB/s".format(bytesPerSecond / 1_000)
    }
    return "${Math.round(bytesPerSecond)} B/s"
}

/** "2h 5m" or "45m". */
fun formatRuntime(minutes: Int?): String? {
    if (minutes == null || minutes <= 0) {
        return null
    }
    val hours = minutes / 60
    if (hours == 0) {
        return "${minutes % 60}m"
    }
    return "${hours}h ${minutes % 60}m"
}

/** "2014", "2h 49m", "★ 8.5" and the first two genres. */
fun movieFacts(detail: TitleDetail): List<String> {
    val facts = mutableListOf<String>()
    detail.year?.let { year -> facts.add(year.toString()) }
    formatRuntime(detail.runtime)?.let { runtime -> facts.add(runtime) }
    if (detail.voteAverage > 0) {
        facts.add("★ %.1f".format(detail.voteAverage))
    }
    if (detail.genres.isNotEmpty()) {
        facts.add(detail.genres.take(2).joinToString(" · "))
    }
    return facts
}

/** "2008", "5 Seasons", "★ 8.9" and the first two genres. */
fun seriesFacts(detail: TitleDetail): List<String> {
    val facts = mutableListOf<String>()
    detail.year?.let { year -> facts.add(year.toString()) }
    if (detail.regularSeasonCount == 1) {
        facts.add("1 Season")
    }
    if (detail.regularSeasonCount > 1) {
        facts.add("${detail.regularSeasonCount} Seasons")
    }
    if (detail.voteAverage > 0) {
        facts.add("★ %.1f".format(detail.voteAverage))
    }
    if (detail.genres.isNotEmpty()) {
        facts.add(detail.genres.take(2).joinToString(" · "))
    }
    return facts
}

fun movieCreditLines(detail: TitleDetail): List<String> {
    val lines = mutableListOf<String>()
    if (detail.cast.isNotEmpty()) {
        lines.add("Starring: " + detail.cast.take(STARRING_COUNT).joinToString(", ") { member -> member.name })
    }
    if (detail.directors.isNotEmpty()) {
        lines.add("Director: " + detail.directors.joinToString(", ") { director -> director.name })
    }
    return lines
}

fun seriesCreditLines(detail: TitleDetail): List<String> {
    if (detail.cast.isEmpty()) {
        return emptyList()
    }
    return listOf("Starring: " + detail.cast.take(STARRING_COUNT).joinToString(", ") { member -> member.name })
}

fun moviePlayLabel(streams: StreamList): String {
    if (streams.vpnBlocked) {
        return "Connect to a VPN to watch"
    }
    if (streams.isBusy) {
        return "Searching sources…"
    }
    if (streams.nothingFound) {
        return "No sources found"
    }
    return "Play"
}

fun seriesPlayLabel(upNext: UpNext?, vpnBlocked: Boolean): String {
    if (vpnBlocked) {
        return "Connect to a VPN to watch"
    }
    if (upNext == null) {
        return "Play"
    }
    if (upNext.resume) {
        return "Resume ${upNext.episode.code}"
    }
    return "Play ${upNext.episode.code}"
}
