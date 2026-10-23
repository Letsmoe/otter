package dev.neoworks.otter.profiles

/** What a profile gets until it picks its own; the backend uses the same default. */
val DEFAULT_LANGUAGES = listOf("de", "en")

data class LanguageChoice(val code: String, val name: String)

/** The languages a profile can pick, in the order the editor shows them. */
val LANGUAGE_CHOICES = listOf(
    LanguageChoice("de", "German"),
    LanguageChoice("en", "English"),
    LanguageChoice("fr", "French"),
    LanguageChoice("es", "Spanish"),
    LanguageChoice("it", "Italian"),
    LanguageChoice("ja", "Japanese"),
)

// Plugins mostly send codes ("de", "de-sub"); older links stored names.
private val LANGUAGE_NAMES = mapOf(
    "deutsch" to "de",
    "german" to "de",
    "english" to "en",
    "englisch" to "en",
)

/** The active profile's audio languages, most preferred first. */
fun preferredLanguages(): List<String> {
    val languages = ProfileSession.activeProfile.value?.languages
    if (languages.isNullOrEmpty()) {
        return DEFAULT_LANGUAGES
    }
    return languages
}

/**
 * Lower is better: each preferred language as a dub, then with subtitles,
 * then links without a language, then languages the profile didn't pick.
 */
fun languageRank(language: String?, preferred: List<String>): Int {
    val unknownRank = preferred.size * 2
    if (language.isNullOrBlank()) {
        return unknownRank
    }
    val normalized = language.trim().lowercase()
    val isSubtitled = normalized.endsWith("-sub")
    val code = baseLanguage(normalized.removeSuffix("-sub"))
    val position = preferred.indexOf(code)
    if (position == -1) {
        return unknownRank + 1
    }
    var rank = position * 2
    if (isSubtitled) {
        rank += 1
    }
    return rank
}

private fun baseLanguage(language: String): String {
    val named = LANGUAGE_NAMES[language]
    if (named != null) {
        return named
    }
    return language.substringBefore("-")
}
