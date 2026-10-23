package dev.neoworks.otter.together

import dev.neoworks.otter.graphql.type.TogetherStatus
import kotlin.math.abs

// Pure helpers mirroring packages/otter-app/src/together.ts.

const val ROOM_CODE_LENGTH = 8

// Room-code alphabet: no I, O, 0 or 1.
private const val ROOM_CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
private val roomCodePattern = Regex("([A-HJ-NP-Z2-9]{4})[-\\s]?([A-HJ-NP-Z2-9]{4})", RegexOption.IGNORE_CASE)

const val INVITE_LINK_PREFIX = "otter://together/"

// Beyond this much drift the player jumps; below it, it catches up by playing slightly faster or slower.
private const val SEEK_DRIFT_SECONDS = 1.0
private const val IN_SYNC_SECONDS = 0.2
private const val CATCH_UP_RATE = 0.05f

fun isRoomCodeCharacter(character: Char): Boolean = character.uppercaseChar() in ROOM_CODE_ALPHABET

/** Only the characters a room code can contain, upper-cased. */
fun roomCodeCharacters(text: String): List<Char> {
    return text.uppercase().filter { character -> character in ROOM_CODE_ALPHABET }.toList()
}

/** "K7QD9MZA" becomes "K7QD-9MZA". */
fun formatRoomCode(code: String): String {
    if (code.length <= 4) {
        return code
    }
    return "${code.take(4)}-${code.drop(4)}"
}

/** Finds a room code in a typed code, an invite link or a pasted invite text. */
fun roomCodeFromText(text: String): String? {
    val match = roomCodePattern.find(text) ?: return null
    return (match.groupValues[1] + match.groupValues[2]).uppercase()
}

fun inviteLink(code: String): String = "$INVITE_LINK_PREFIX$code"

fun inviteText(code: String): String {
    return "Watch with me on Otter! Room code ${formatRoomCode(code)} — or open ${inviteLink(code)}"
}

/** Peers still in the room: hosting alone, connecting or connected. */
fun TogetherRoom?.isOpen(): Boolean {
    if (this == null) {
        return false
    }
    return status == TogetherStatus.WAITING || status == TogetherStatus.CONNECTING || status == TogetherStatus.CONNECTED
}

fun TogetherRoom?.otherPeers(): List<TogetherPeer> {
    if (this == null) {
        return emptyList()
    }
    return peers.filter { peer -> peer.id != self.id }
}

/** Peers who would fall behind if playback started: not on this title yet, or still loading it. */
fun peersNotReady(peers: List<TogetherPeer>, states: Map<String, PeerPlayerState>, mediaId: String): List<TogetherPeer> {
    return peers.filter { peer ->
        val state = states[peer.id]
        state == null || state.mediaId != mediaId || state.buffering
    }
}

/** Where remote playback is now, given when its message arrived. */
fun expectedPosition(playback: PlaybackPayload, receivedAtMilliseconds: Long, nowMilliseconds: Long): Double {
    if (!playback.playing) {
        return playback.position
    }
    return playback.position + (nowMilliseconds - receivedAtMilliseconds) / 1000.0
}

fun needsSeek(localPosition: Double, remotePosition: Double): Boolean {
    return abs(localPosition - remotePosition) > SEEK_DRIFT_SECONDS
}

/** Playback-rate factor that closes small drift: above 1 when behind, below 1 when ahead. */
fun catchUpRate(localPosition: Double, remotePosition: Double): Float {
    val behindBy = remotePosition - localPosition
    if (abs(behindBy) <= IN_SYNC_SECONDS) {
        return 1f
    }
    if (behindBy > 0) {
        return 1f + CATCH_UP_RATE
    }
    return 1f - CATCH_UP_RATE
}
