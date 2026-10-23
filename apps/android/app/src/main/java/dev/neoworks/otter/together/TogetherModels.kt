package dev.neoworks.otter.together

import dev.neoworks.otter.graphql.fragment.TogetherPeerFields
import dev.neoworks.otter.graphql.fragment.TogetherRoomFields
import dev.neoworks.otter.graphql.type.TogetherStatus

data class TogetherPeer(val id: String, val name: String, val avatar: String?)

data class TogetherRoom(
    val code: String,
    val hosting: Boolean,
    val status: TogetherStatus,
    val self: TogetherPeer,
    val peers: List<TogetherPeer>,
)

/** The original link someone plays; the others store and resolve it themselves. */
data class SharedSource(val url: String, val plugin: String, val label: String)

/** What a peer's player last reported. */
data class PeerPlayerState(val mediaId: String?, val buffering: Boolean)

data class ChatLine(val id: Long, val from: TogetherPeer, val text: String, val mine: Boolean)

data class FloatingReaction(val id: Long, val emoji: String, val from: TogetherPeer, val offsetPercent: Int)

sealed interface TogetherPayload

/** What is playing: lets the others open the same title and source. */
data class MediaPayload(
    val mediaId: String,
    /** App path that plays the title; read by the web apps, ignored here. */
    val route: String,
    val title: String,
    val source: SharedSource?,
) : TogetherPayload

data class PlaybackPayload(
    val mediaId: String,
    val playing: Boolean,
    val position: Double,
    /** Periodic sync from the host rather than someone pressing a control. */
    val heartbeat: Boolean,
) : TogetherPayload

data class StatusPayload(val mediaId: String?, val buffering: Boolean) : TogetherPayload

data class ChatPayload(val text: String) : TogetherPayload

data class ReactionPayload(val emoji: String) : TogetherPayload

data class RemotePlayback(val payload: PlaybackPayload, val receivedAtMilliseconds: Long)

data class TogetherMessage(val from: TogetherPeer, val payload: String)

fun TogetherPeerFields.toPeer(): TogetherPeer = TogetherPeer(id, name, avatar)

fun TogetherRoomFields.toRoom(): TogetherRoom {
    return TogetherRoom(code, hosting, status, self.togetherPeerFields.toPeer(), peers.map { peer -> peer.togetherPeerFields.toPeer() })
}
