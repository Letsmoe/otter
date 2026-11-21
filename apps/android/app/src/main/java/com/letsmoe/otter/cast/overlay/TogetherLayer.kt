package com.letsmoe.otter.cast.overlay

// How long a reaction rises and a chat line stays, in the stream's seconds.
private const val REACTION_SECONDS = 2.6
private const val CHAT_SECONDS = 7.0
private const val MAX_CHAT_LINES = 2
// Steps of a reaction's rise: smooth enough, and frames between steps repeat.
private const val REACTION_STEPS = 60

/** An emoji someone in the Watch Together room sent. */
data class TogetherReaction(val id: Long, val emoji: String, val name: String)

/** A chat line from the Watch Together room. */
data class TogetherChatLine(val id: Long, val name: String, val text: String)

/** What the room sends right now: the reactions still floating and the latest chat lines. */
data class TogetherMoments(
    val reactions: List<TogetherReaction> = emptyList(),
    val chat: List<TogetherChatLine> = emptyList(),
)

/** A reaction in the picture; [progress] runs from 0 (just sent) to 1 (gone). */
data class ReactionMark(val emoji: String, val name: String, val progress: Float, val lane: Int)

/** What the picture shows of the room at one moment. */
data class TogetherMarks(val reactions: List<ReactionMark> = emptyList(), val chat: List<TogetherChatLine> = emptyList())

/**
 * Times the room's reactions and chat on the stream's clock: each shows from
 * the frame it first arrived in. What was there before the stream started is
 * old news and isn't drawn.
 */
class TogetherLayer {

    private val firstSeen = mutableMapOf<Long, Double>()
    private var started = false

    fun marksAt(seconds: Double, moments: TogetherMoments): TogetherMarks {
        if (!started) {
            started = true
            forgetEarlier(moments)
        }
        val reactions = moments.reactions.mapNotNull { reaction -> reactionMark(reaction, seconds) }
        val chat = moments.chat.filter { line -> isFresh(line.id, seconds, CHAT_SECONDS) }.takeLast(MAX_CHAT_LINES)
        return TogetherMarks(reactions, chat)
    }

    private fun forgetEarlier(moments: TogetherMoments) {
        moments.reactions.forEach { reaction -> firstSeen[reaction.id] = Double.NEGATIVE_INFINITY }
        moments.chat.forEach { line -> firstSeen[line.id] = Double.NEGATIVE_INFINITY }
    }

    private fun reactionMark(reaction: TogetherReaction, seconds: Double): ReactionMark? {
        val shownAt = firstSeen.getOrPut(reaction.id) { seconds }
        val progress = (seconds - shownAt) / REACTION_SECONDS
        if (progress < 0 || progress >= 1) {
            return null
        }
        val stepped = (progress * REACTION_STEPS).toInt().toFloat() / REACTION_STEPS
        return ReactionMark(reaction.emoji, reaction.name, stepped, (reaction.id % 3).toInt())
    }

    private fun isFresh(id: Long, seconds: Double, durationSeconds: Double): Boolean {
        val shownAt = firstSeen.getOrPut(id) { seconds }
        return seconds - shownAt < durationSeconds
    }
}
