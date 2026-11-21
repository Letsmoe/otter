package com.letsmoe.otter.together

/** Every person may put this many titles on the ballot. */
const val MAX_SUGGESTIONS_PER_PERSON = 3

/** A title on the ballot and who put it there. */
data class Suggestion(
    val mediaId: String,
    val title: String,
    val year: Int?,
    val posterPath: String?,
    val suggestedBy: String,
)

/**
 * A movie night's vote: the titles suggested and each person's one vote. Messages from different
 * people arrive in any order, so a vote may come before the suggestion it is for; it counts once
 * the suggestion is there.
 */
data class Ballot(
    val suggestions: List<Suggestion> = emptyList(),
    /** Person id to the media id they voted for. */
    val votes: Map<String, String> = emptyMap(),
) {
    val isEmpty: Boolean
        get() = suggestions.isEmpty()

    fun suggestionsOf(personId: String): List<Suggestion> = suggestions.filter { suggestion -> suggestion.suggestedBy == personId }

    fun isSuggested(mediaId: String): Boolean = suggestions.any { suggestion -> suggestion.mediaId == mediaId }

    /** False for a title already on the ballot, and once the person has used their suggestions. */
    fun canSuggest(personId: String, mediaId: String): Boolean {
        return !isSuggested(mediaId) && suggestionsOf(personId).size < MAX_SUGGESTIONS_PER_PERSON
    }

    fun withSuggestion(suggestion: Suggestion): Ballot {
        if (!canSuggest(suggestion.suggestedBy, suggestion.mediaId)) {
            return this
        }
        return copy(suggestions = suggestions + suggestion)
    }

    /** The person's vote, replacing their earlier one. */
    fun withVote(personId: String, mediaId: String): Ballot = copy(votes = votes + (personId to mediaId))

    /** Without the votes of people who are no longer in the room. */
    fun onlyVotesOf(presentPersonIds: Set<String>): Ballot {
        return copy(votes = votes.filterKeys { personId -> personId in presentPersonIds })
    }

    /** Votes per suggested title, in the order they were suggested; titles nobody voted for have 0. */
    fun tally(): Map<String, Int> {
        val counts = suggestions.associate { suggestion -> suggestion.mediaId to 0 }.toMutableMap()
        for (mediaId in votes.values) {
            if (mediaId in counts) {
                counts[mediaId] = counts.getValue(mediaId) + 1
            }
        }
        return counts
    }

    fun votedFor(personId: String): String? = votes[personId]

    /** How many of [personIds] have a vote that counts. */
    fun votersAmong(personIds: Collection<String>): Int {
        return personIds.count { personId -> hasCountingVote(personId) }
    }

    private fun hasCountingVote(personId: String): Boolean {
        val mediaId = votes[personId] ?: return false
        return isSuggested(mediaId)
    }

    fun everyoneVoted(personIds: Collection<String>): Boolean {
        return !isEmpty && personIds.isNotEmpty() && votersAmong(personIds) == personIds.size
    }

    /** The title with the most votes; of equal ones the one suggested first. Null while nobody voted. */
    fun winner(): Suggestion? {
        val counts = tally()
        val best = counts.values.maxOrNull() ?: return null
        if (best == 0) {
            return null
        }
        return suggestions.first { suggestion -> counts[suggestion.mediaId] == best }
    }
}
