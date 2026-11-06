package com.letsmoe.otter.updates

/** Release tags and version names look like "v1.2.0" or "1.2.0-beta.1". */
private data class ParsedVersion(val numbers: List<Int>, val preRelease: String)

private fun parseVersion(version: String): ParsedVersion {
    val withoutPrefix = version.trim().removePrefix("v").removePrefix("V")
    val core = withoutPrefix.substringBefore('-')
    val preRelease = withoutPrefix.substringAfter('-', "")
    val numbers = core.split('.').map { part -> part.toIntOrNull() ?: 0 }
    return ParsedVersion(numbers, preRelease)
}

/** Whether [candidate] is a later version than [installed]; a pre-release sorts before its release. */
fun isNewerVersion(candidate: String, installed: String): Boolean {
    val candidateVersion = parseVersion(candidate)
    val installedVersion = parseVersion(installed)
    val length = maxOf(candidateVersion.numbers.size, installedVersion.numbers.size)
    for (index in 0 until length) {
        val candidateNumber = candidateVersion.numbers.getOrElse(index) { 0 }
        val installedNumber = installedVersion.numbers.getOrElse(index) { 0 }
        if (candidateNumber != installedNumber) {
            return candidateNumber > installedNumber
        }
    }
    return isLaterPreRelease(candidateVersion.preRelease, installedVersion.preRelease)
}

private fun isLaterPreRelease(candidate: String, installed: String): Boolean {
    if (candidate == installed) {
        return false
    }
    if (candidate.isEmpty()) {
        return true
    }
    if (installed.isEmpty()) {
        return false
    }
    return candidate > installed
}
