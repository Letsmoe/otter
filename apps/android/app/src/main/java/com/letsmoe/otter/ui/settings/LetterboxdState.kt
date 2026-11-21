package com.letsmoe.otter.ui.settings

import com.letsmoe.otter.graphql.fragment.LetterboxdAccountFields
import com.letsmoe.otter.graphql.fragment.LetterboxdImportFields
import com.letsmoe.otter.graphql.type.LetterboxdImportState

enum class ImportPhase { IDLE, RUNNING, DONE, FAILED }

/** A film of the export TMDB had no match for. */
data class MissingFilm(val name: String, val year: Int?, val reason: String)

/** How the profile's latest Letterboxd import stands. */
data class ImportProgress(
    val phase: ImportPhase = ImportPhase.IDLE,
    val total: Int = 0,
    val processed: Int = 0,
    val matched: Int = 0,
    val watched: Int = 0,
    val rated: Int = 0,
    val watchlisted: Int = 0,
    val unmatchedCount: Int = 0,
    val notMatched: List<MissingFilm> = emptyList(),
    val error: String? = null,
)

/** The Letterboxd account a profile follows, and how its last sync went. */
data class FollowedAccount(
    val username: String,
    val lastSyncedAt: String?,
    val lastError: String?,
    val lastWatched: Int,
    val lastRated: Int,
)

fun LetterboxdImportFields.toProgress(): ImportProgress {
    return ImportProgress(
        phase = phaseOf(state),
        total = total,
        processed = processed,
        matched = matched,
        watched = watched,
        rated = rated,
        watchlisted = watchlisted,
        unmatchedCount = unmatchedCount,
        notMatched = notMatched.map { film -> MissingFilm(film.name, film.year, film.reason) },
        error = error,
    )
}

fun LetterboxdAccountFields.toAccount(): FollowedAccount {
    return FollowedAccount(username, lastSyncedAt, lastError, lastWatched, lastRated)
}

private fun phaseOf(state: LetterboxdImportState): ImportPhase {
    return when (state) {
        LetterboxdImportState.RUNNING -> ImportPhase.RUNNING
        LetterboxdImportState.DONE -> ImportPhase.DONE
        LetterboxdImportState.FAILED -> ImportPhase.FAILED
        else -> ImportPhase.IDLE
    }
}

/** Matching films, "120 of 340"; empty before the export's films are counted. */
fun ImportProgress.progressText(): String {
    if (total == 0) {
        return "Reading the export…"
    }
    return "Matching films with TMDB: $processed of $total"
}

fun ImportProgress.progressFraction(): Float {
    if (total == 0) {
        return 0f
    }
    return (processed.toFloat() / total).coerceIn(0f, 1f)
}

/** What a finished import did, one line each; films Otter already had are not counted. */
fun ImportProgress.summaryLines(): List<String> {
    val lines = mutableListOf<String>()
    lines.add("${count(watched, "film")} marked as watched")
    lines.add("${count(rated, "rating")} set")
    lines.add("${count(watchlisted, "film")} added to My List")
    if (unmatchedCount > 0) {
        lines.add("${count(unmatchedCount, "film")} not matched")
    }
    return lines
}

/** "1 film", "3 films". */
fun count(number: Int, noun: String): String {
    if (number == 1) {
        return "1 $noun"
    }
    return "$number ${noun}s"
}

/** "Heat (1995)", or just the name when the export has no year. */
fun MissingFilm.label(): String {
    if (year == null) {
        return name
    }
    return "$name ($year)"
}

/** What the last sync of a followed account did, or why it failed. */
fun FollowedAccount.syncLine(): String {
    if (lastError != null) {
        return lastError
    }
    if (lastSyncedAt == null) {
        return "Not read yet"
    }
    return "Last sync: $lastWatched new watched, $lastRated new rated"
}
