package com.letsmoe.otter.together

import com.letsmoe.otter.AppScope
import com.letsmoe.otter.toasts.ToastKind
import com.letsmoe.otter.toasts.Toasts
import com.letsmoe.otter.ui.title.fetchUpNextEpisodeId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// With fewer people the vote only closes when the host closes it.
private const val MINIMUM_PEOPLE_FOR_AUTOMATIC_CLOSE = 2

/**
 * Movie night in a Watch Together room: everyone suggests up to three titles, everyone votes on
 * their phone and sees the counts live, and when everyone has voted (or the host closes the vote)
 * the winner opens for the whole room. The host decides, so a vote changed at the last moment
 * cannot give two phones two winners: guests follow the host's result.
 */
object MovieNight {

    private val mutableBallot = MutableStateFlow(Ballot())
    val ballot: StateFlow<Ballot> = mutableBallot

    /** The movie night sheet over the app. */
    private val mutableSheetOpen = MutableStateFlow(false)
    val sheetOpen: StateFlow<Boolean> = mutableSheetOpen

    fun openSheet() {
        mutableSheetOpen.value = true
    }

    fun closeSheet() {
        mutableSheetOpen.value = false
    }

    /** Puts a title on the ballot, when this person still may. */
    fun suggest(mediaId: String, title: String, year: Int?, posterPath: String?) {
        val room = TogetherSession.room.value ?: return
        val suggestion = Suggestion(mediaId, title, year, posterPath, room.self.id)
        if (!mutableBallot.value.canSuggest(room.self.id, mediaId)) {
            return
        }
        mutableBallot.update { current -> current.withSuggestion(suggestion) }
        TogetherSession.send(SuggestPayload(mediaId, title, year, posterPath))
        closeAutomaticallyIfEveryoneVoted()
    }

    /** This person's vote; voting again moves it. */
    fun vote(mediaId: String) {
        val room = TogetherSession.room.value ?: return
        if (!mutableBallot.value.isSuggested(mediaId)) {
            return
        }
        mutableBallot.update { current -> current.withVote(room.self.id, mediaId) }
        TogetherSession.send(VotePayload(mediaId))
        closeAutomaticallyIfEveryoneVoted()
    }

    /** The host ends the vote now; the title with the most votes wins. */
    fun closeVote() {
        val room = TogetherSession.room.value
        if (room == null || !room.hosting) {
            return
        }
        val winner = mutableBallot.value.winner() ?: return
        announceWinner(winner)
    }

    fun onPayload(from: TogetherPeer, payload: TogetherPayload) {
        when (payload) {
            is SuggestPayload -> onSuggestion(from, payload)
            is VotePayload -> onVote(from, payload)
            is VoteResultPayload -> onResult(payload)
            else -> Unit
        }
    }

    private fun onSuggestion(from: TogetherPeer, payload: SuggestPayload) {
        val suggestion = Suggestion(payload.mediaId, payload.title, payload.year, payload.posterPath, from.id)
        mutableBallot.update { current -> current.withSuggestion(suggestion) }
        closeAutomaticallyIfEveryoneVoted()
    }

    private fun onVote(from: TogetherPeer, payload: VotePayload) {
        mutableBallot.update { current -> current.withVote(from.id, payload.mediaId) }
        closeAutomaticallyIfEveryoneVoted()
    }

    private fun onResult(payload: VoteResultPayload) {
        mutableBallot.value = Ballot()
        Toasts.push("${payload.title} won the vote")
    }

    /** People left or arrived: their votes stop counting, and the vote may now be complete. */
    fun onRoomChanged(room: TogetherRoom) {
        val present = room.peers.map { peer -> peer.id }.toSet()
        mutableBallot.update { current -> current.onlyVotesOf(present) }
        closeAutomaticallyIfEveryoneVoted()
    }

    /** Someone joined: tell them this person's suggestions and vote. */
    fun resend() {
        val room = TogetherSession.room.value ?: return
        val current = mutableBallot.value
        for (suggestion in current.suggestionsOf(room.self.id)) {
            TogetherSession.send(SuggestPayload(suggestion.mediaId, suggestion.title, suggestion.year, suggestion.posterPath))
        }
        val voted = current.votedFor(room.self.id) ?: return
        TogetherSession.send(VotePayload(voted))
    }

    fun reset() {
        mutableBallot.value = Ballot()
        mutableSheetOpen.value = false
    }

    // Only the host closes, so everyone gets the same winner.
    private fun closeAutomaticallyIfEveryoneVoted() {
        val room = TogetherSession.room.value
        if (room == null || !room.hosting || room.peers.size < MINIMUM_PEOPLE_FOR_AUTOMATIC_CLOSE) {
            return
        }
        val ballot = mutableBallot.value
        if (!ballot.everyoneVoted(room.peers.map { peer -> peer.id })) {
            return
        }
        val winner = ballot.winner() ?: return
        announceWinner(winner)
    }

    // The ballot is cleared first, so the last votes arriving meanwhile cannot close it twice.
    private fun announceWinner(winner: Suggestion) {
        mutableBallot.value = Ballot()
        TogetherSession.send(VoteResultPayload(winner.mediaId, winner.title))
        Toasts.push("${winner.title} won the vote")
        AppScope.launch { openWinner(winner) }
    }

    private suspend fun openWinner(winner: Suggestion) {
        val media = mediaForWinner(winner)
        if (media == null) {
            Toasts.push("Could not open ${winner.title}", ToastKind.ERROR)
            return
        }
        TogetherSession.openForRoom(media)
    }

    // A series plays the host's up-next episode, which the others open too; null when it has none.
    private suspend fun mediaForWinner(winner: Suggestion): MediaPayload? {
        if (!winner.mediaId.startsWith("series:")) {
            return MediaPayload(winner.mediaId, "/movies/${winner.mediaId}", winner.title, null)
        }
        val episodeId = fetchUpNextEpisodeId(winner.mediaId) ?: return null
        return MediaPayload(episodeId, "/series/${winner.mediaId}?episode=$episodeId", winner.title, null)
    }
}
