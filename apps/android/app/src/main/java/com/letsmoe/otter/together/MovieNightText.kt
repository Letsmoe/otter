package com.letsmoe.otter.together

/** "2 titles · 1 of 3 voted", or a hint while nothing is suggested. */
fun voteSummary(ballot: Ballot, room: TogetherRoom): String {
    if (ballot.isEmpty) {
        return "Suggest titles and vote on one together"
    }
    val titles = ballot.suggestions.size
    var titlesText = "$titles titles"
    if (titles == 1) {
        titlesText = "1 title"
    }
    val voted = ballot.votersAmong(room.peers.map { peer -> peer.id })
    return "$titlesText · $voted of ${room.peers.size} voted"
}

/** What the footer says: what the vote waits for, from this person's side. */
fun voteStatus(ballot: Ballot, room: TogetherRoom): String {
    if (ballot.isEmpty) {
        return "Nothing to vote on yet."
    }
    if (room.hosting) {
        return "Closes when everyone voted, or when you close it."
    }
    return "Closes when everyone voted, or when the host closes it."
}

/** Who suggested a title, as "you" or their name. */
fun suggestedByText(suggestion: Suggestion, room: TogetherRoom): String {
    if (suggestion.suggestedBy == room.self.id) {
        return "suggested by you"
    }
    val person = room.peers.firstOrNull { peer -> peer.id == suggestion.suggestedBy } ?: return "suggested by someone"
    return "suggested by ${person.name}"
}

/** The TMDB poster path ("/abc.jpg") inside a poster URL; others rebuild their own URL from it. */
fun posterPathOf(posterUrl: String?): String? {
    if (posterUrl == null) {
        return null
    }
    val fileName = posterUrl.substringAfterLast('/')
    if (fileName.isEmpty()) {
        return null
    }
    return "/$fileName"
}

/** How many suggestions this person has left. */
fun suggestionsLeft(ballot: Ballot, room: TogetherRoom): Int {
    return (MAX_SUGGESTIONS_PER_PERSON - ballot.suggestionsOf(room.self.id).size).coerceAtLeast(0)
}
