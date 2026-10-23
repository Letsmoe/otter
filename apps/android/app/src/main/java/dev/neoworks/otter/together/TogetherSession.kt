package dev.neoworks.otter.together

import dev.neoworks.otter.AppScope
import dev.neoworks.otter.api.OtterApi
import dev.neoworks.otter.api.dataOrThrow
import dev.neoworks.otter.graphql.HostTogetherMutation
import dev.neoworks.otter.graphql.JoinTogetherMutation
import dev.neoworks.otter.graphql.LeaveTogetherMutation
import dev.neoworks.otter.graphql.SendTogetherMutation
import dev.neoworks.otter.graphql.TogetherUpdatesSubscription
import dev.neoworks.otter.graphql.type.TogetherStatus
import dev.neoworks.otter.toasts.ToastKind
import dev.neoworks.otter.toasts.Toasts
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What the open player tells the session. */
class PlayerReport(
    val media: MediaPayload,
    val isPlaying: () -> Boolean,
    val position: () -> Double,
)

/**
 * The Watch Together session: room state, chat, reactions and the others'
 * playback. The player reports what it plays and what the user does; the app
 * opens what someone else started (remoteMedia). Mirrors stores/together.svelte.ts.
 */
object TogetherSession {

    private const val CHAT_LIMIT = 50
    private const val REACTION_MILLISECONDS = 2600L
    private const val RESUBSCRIBE_DELAY_MILLISECONDS = 2000L
    private const val MAX_REACTION_OFFSET = 60

    private val mutableRoom = MutableStateFlow<TogetherRoom?>(null)
    val room: StateFlow<TogetherRoom?> = mutableRoom

    private val mutableChat = MutableStateFlow<List<ChatLine>>(emptyList())
    val chat: StateFlow<List<ChatLine>> = mutableChat

    private val mutableReactions = MutableStateFlow<List<FloatingReaction>>(emptyList())
    val reactions: StateFlow<List<FloatingReaction>> = mutableReactions

    /** Each peer's player: what it shows and whether it's still loading. */
    private val mutablePeerStates = MutableStateFlow<Map<String, PeerPlayerState>>(emptyMap())
    val peerStates: StateFlow<Map<String, PeerPlayerState>> = mutablePeerStates

    /** Title whose playback starts even though not everyone is ready. */
    private val mutableHoldOverride = MutableStateFlow<String?>(null)
    val holdOverrideMediaId: StateFlow<String?> = mutableHoldOverride

    /** Media someone else started that this device hasn't opened yet. */
    private val mutableRemoteMedia = MutableStateFlow<MediaPayload?>(null)
    val remoteMedia: StateFlow<MediaPayload?> = mutableRemoteMedia

    private val mutableRemotePlayback = MutableStateFlow<RemotePlayback?>(null)
    val remotePlayback: StateFlow<RemotePlayback?> = mutableRemotePlayback

    private val mutableBusy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = mutableBusy

    /** The Watch Together sheet over the app. */
    private val mutableSheetOpen = MutableStateFlow(false)
    val sheetOpen: StateFlow<Boolean> = mutableSheetOpen

    /** The chat sheet, opened from the Watch Together sheet. */
    private val mutableChatOpen = MutableStateFlow(false)
    val chatOpen: StateFlow<Boolean> = mutableChatOpen

    /** How many chat lines the user has seen, for the unread count. */
    private val mutableSeenChatCount = MutableStateFlow(0)
    val seenChatCount: StateFlow<Int> = mutableSeenChatCount

    /** Code from an invite link, prefilled in the sheet. */
    private val mutableInviteCode = MutableStateFlow("")
    val inviteCode: StateFlow<String> = mutableInviteCode

    private val mutableNowPlayingTitle = MutableStateFlow<String?>(null)

    /** Title the room last started, or null while nothing is playing. */
    val nowPlayingTitle: StateFlow<String?> = mutableNowPlayingTitle

    private var subscriptionJob: Job? = null
    private var player: PlayerReport? = null
    private var knownPeerIds = emptySet<String>()

    // What the room watches as far as this device knows; announcing it again would make everyone reopen it.
    private var roomMediaId: String? = null
    private var ownState = PeerPlayerState(mediaId = null, buffering = false)
    private var counter = 0L

    val isConnected: Boolean
        get() = mutableRoom.value?.status == TogetherStatus.CONNECTED

    fun openSheet(inviteCode: String = "") {
        mutableInviteCode.value = inviteCode
        mutableSheetOpen.value = true
    }

    fun closeSheet() {
        mutableSheetOpen.value = false
    }

    fun openChat() {
        mutableChatOpen.value = true
        markChatSeen()
    }

    fun closeChat() {
        mutableChatOpen.value = false
    }

    fun markChatSeen() {
        mutableSeenChatCount.value = mutableChat.value.size
    }

    /** Follows the backend's room for the app's lifetime. */
    fun start() {
        if (subscriptionJob != null) {
            return
        }
        subscriptionJob = AppScope.launch { followBackend() }
    }

    private suspend fun followBackend() {
        OtterApi.awaitBackend()
        while (true) {
            try {
                OtterApi.client.subscription(TogetherUpdatesSubscription()).toFlow().collect { response ->
                    val update = response.data?.together
                    if (update != null) {
                        onUpdate(update)
                    }
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                // Resubscribe below.
            }
            delay(RESUBSCRIBE_DELAY_MILLISECONDS)
        }
    }

    suspend fun host(profileId: String): Boolean {
        return enter { OtterApi.client.mutation(HostTogetherMutation(profileId)).execute().dataOrThrow().hostTogether.togetherRoomFields.toRoom() }
    }

    suspend fun join(profileId: String, code: String): Boolean {
        return enter { OtterApi.client.mutation(JoinTogetherMutation(profileId, code)).execute().dataOrThrow().joinTogether.togetherRoomFields.toRoom() }
    }

    suspend fun leave() {
        reset()
        mutableRoom.value = null
        try {
            OtterApi.client.mutation(LeaveTogetherMutation()).execute()
        } catch (error: Exception) {
            // The room is gone locally either way.
        }
    }

    private suspend fun enter(open: suspend () -> TogetherRoom): Boolean {
        mutableBusy.value = true
        reset()
        try {
            val opened = open()
            mutableRoom.value = opened
            knownPeerIds = opened.peers.map { peer -> peer.id }.toSet()
            return true
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            Toasts.push("Could not open Watch Together", ToastKind.ERROR)
            return false
        } finally {
            mutableBusy.value = false
        }
    }

    private fun reset() {
        mutableChat.value = emptyList()
        mutableSeenChatCount.value = 0
        mutableChatOpen.value = false
        mutableReactions.value = emptyList()
        mutablePeerStates.value = emptyMap()
        mutableHoldOverride.value = null
        mutableRemoteMedia.value = null
        mutableRemotePlayback.value = null
        knownPeerIds = emptySet()
        roomMediaId = null
        mutableNowPlayingTitle.value = null
    }

    // Player -----------------------------------------------------------------

    /** The player opened or switched title; the others follow. */
    fun attachPlayer(report: PlayerReport) {
        player = report
        if (!isConnected || roomMediaId == report.media.mediaId) {
            return
        }
        roomMediaId = report.media.mediaId
        mutableNowPlayingTitle.value = report.media.title
        send(report.media)
    }

    fun detachPlayer(mediaId: String) {
        if (player?.media?.mediaId != mediaId) {
            return
        }
        player = null
        announcePlayerState(null, false)
    }

    /** The user pressed play, pause or seeked. */
    fun announcePlayback(heartbeat: Boolean = false) {
        val report = player ?: return
        if (!isConnected) {
            return
        }
        send(PlaybackPayload(report.media.mediaId, report.isPlaying(), report.position(), heartbeat))
    }

    /** This player's title and whether it is still loading; the others wait for it. */
    fun announcePlayerState(mediaId: String?, buffering: Boolean) {
        ownState = PeerPlayerState(mediaId, buffering)
        if (!isConnected) {
            return
        }
        send(StatusPayload(mediaId, buffering))
    }

    /** Others who haven't loaded this title yet; playback holds until they have. */
    fun holdingFor(
        mediaId: String?,
        currentRoom: TogetherRoom?,
        states: Map<String, PeerPlayerState>,
        override: String?,
    ): List<TogetherPeer> {
        val connected = currentRoom?.status == TogetherStatus.CONNECTED
        if (mediaId == null || !connected || override == mediaId) {
            return emptyList()
        }
        return peersNotReady(currentRoom.otherPeers(), states, mediaId)
    }

    fun startWithoutWaiting(mediaId: String) {
        mutableHoldOverride.value = mediaId
    }

    fun sendChat(text: String) {
        val trimmed = text.trim()
        val current = mutableRoom.value
        if (trimmed.isEmpty() || current == null) {
            return
        }
        addChat(current.self, trimmed, mine = true)
        send(ChatPayload(trimmed))
    }

    fun sendReaction(emoji: String) {
        val current = mutableRoom.value ?: return
        addReaction(current.self, emoji)
        send(ReactionPayload(emoji))
    }

    /** A screen takes the media it was opened for, so it plays only once. */
    fun takeRemoteMedia(mediaId: String): MediaPayload? {
        val pending = mutableRemoteMedia.value
        if (pending == null || pending.mediaId != mediaId) {
            return null
        }
        mutableRemoteMedia.value = null
        return pending
    }

    private fun send(payload: TogetherPayload) {
        AppScope.launch {
            try {
                OtterApi.client.mutation(SendTogetherMutation(payload.toJson())).execute()
            } catch (error: Exception) {
                // Best effort: the others resync on the next heartbeat.
            }
        }
    }

    // Incoming ---------------------------------------------------------------

    private fun onUpdate(update: TogetherUpdatesSubscription.Together) {
        val previous = mutableRoom.value
        val current = update.room?.togetherRoomFields?.toRoom()
        mutableRoom.value = current
        announceRoomChanges(previous, current)
        val message = update.message
        if (message != null) {
            onMessage(TogetherMessage(message.from.togetherPeerFields.toPeer(), message.payload))
        }
    }

    private fun announceRoomChanges(previous: TogetherRoom?, current: TogetherRoom?) {
        if (current == null) {
            return
        }
        if (previous?.status != current.status) {
            announceStatus(current)
        }
        val joined = current.peers.filter { peer -> peer.id !in knownPeerIds }
        val left = peersMissingFrom(previous, current)
        knownPeerIds = current.peers.map { peer -> peer.id }.toSet()
        joined.forEach { peer -> Toasts.push("${peer.name} joined Watch Together", ToastKind.SUCCESS) }
        left.forEach { peer -> Toasts.push("${peer.name} left Watch Together") }
        if (joined.isNotEmpty()) {
            catchUp()
        }
    }

    private fun announceStatus(room: TogetherRoom) {
        if (room.status == TogetherStatus.CLOSED) {
            Toasts.push("Watch Together ended")
        }
        if (room.status == TogetherStatus.FAILED) {
            Toasts.push("Could not reach the host", ToastKind.ERROR)
        }
    }

    // Someone new joined: tell them what is playing and where.
    private fun catchUp() {
        if (!isConnected) {
            return
        }
        send(StatusPayload(ownState.mediaId, ownState.buffering))
        val report = player ?: return
        roomMediaId = report.media.mediaId
        mutableNowPlayingTitle.value = report.media.title
        send(report.media)
        announcePlayback(heartbeat = true)
    }

    private fun onMessage(message: TogetherMessage) {
        when (val payload = parseTogetherPayload(message.payload)) {
            is MediaPayload -> onMedia(payload)
            is PlaybackPayload -> mutableRemotePlayback.value = RemotePlayback(payload, System.currentTimeMillis())
            is StatusPayload -> onPeerState(message.from, payload)
            is ChatPayload -> onChat(message.from, payload.text)
            is ReactionPayload -> addReaction(message.from, payload.emoji)
            null -> Unit
        }
    }

    private fun onPeerState(from: TogetherPeer, state: StatusPayload) {
        mutablePeerStates.update { current -> current + (from.id to PeerPlayerState(state.mediaId, state.buffering)) }
    }

    private fun onMedia(media: MediaPayload) {
        if (player?.media?.mediaId == media.mediaId) {
            return
        }
        mutableRemoteMedia.value = media
        mutableRemotePlayback.value = null
        roomMediaId = media.mediaId
        mutableNowPlayingTitle.value = media.title
    }

    private fun onChat(from: TogetherPeer, text: String) {
        addChat(from, text, mine = false)
        // The player shows chat itself.
        if (player == null) {
            Toasts.push("${from.name}: $text")
        }
    }

    private fun addChat(from: TogetherPeer, text: String, mine: Boolean) {
        counter += 1
        val line = ChatLine(counter, from, text, mine)
        mutableChat.update { current -> (current + line).takeLast(CHAT_LIMIT) }
    }

    private fun addReaction(from: TogetherPeer, emoji: String) {
        counter += 1
        val reaction = FloatingReaction(counter, emoji, from, (Math.random() * MAX_REACTION_OFFSET).toInt())
        mutableReactions.update { current -> current + reaction }
        AppScope.launch {
            delay(REACTION_MILLISECONDS)
            mutableReactions.update { current -> current.filter { candidate -> candidate.id != reaction.id } }
        }
    }

    private fun peersMissingFrom(previous: TogetherRoom?, current: TogetherRoom): List<TogetherPeer> {
        if (previous == null) {
            return emptyList()
        }
        val currentIds = current.peers.map { peer -> peer.id }.toSet()
        return previous.peers.filter { peer -> peer.id !in currentIds }
    }
}
