package com.letsmoe.otter.cast

private const val MAC_BYTES = 6
private const val MAGIC_PACKET_REPEATS = 16

// Six pairs of hex digits, separated by colons or dashes, not part of a longer run of pairs (an EUI-64 or a serial).
private val macPattern = Regex(
    "(?<![0-9A-Fa-f])(?<![0-9A-Fa-f]{2}[:-])" +
        "([0-9A-Fa-f]{2}([:-])(?:[0-9A-Fa-f]{2}\\2){4}[0-9A-Fa-f]{2})" +
        "(?![0-9A-Fa-f])(?![:-][0-9A-Fa-f]{2})",
)
private val typedMacPattern = Regex("^[0-9A-Fa-f]{2}([:-]?)([0-9A-Fa-f]{2}\\1){4}[0-9A-Fa-f]{2}$")

/** "AA:BB:CC:DD:EE:FF" (or with dashes, or without separators) as its six bytes; null when it isn't one. */
fun parseMacAddress(text: String): ByteArray? {
    val trimmed = text.trim()
    if (!typedMacPattern.matches(trimmed)) {
        return null
    }
    val digits = trimmed.filter { character -> character != ':' && character != '-' }
    return ByteArray(MAC_BYTES) { index -> digits.substring(index * 2, index * 2 + 2).toInt(radix = 16).toByte() }
}

fun formatMacAddress(address: ByteArray): String {
    return address.joinToString(":") { byte -> "%02X".format(byte) }
}

/** The first MAC address written out in [text] (a UPnP device description, say); null when there is none. */
fun findMacAddress(text: String): String? {
    for (match in macPattern.findAll(text)) {
        val address = parseMacAddress(match.groupValues[1]) ?: continue
        if (isUsableMacAddress(address)) {
            return formatMacAddress(address)
        }
    }
    return null
}

// All zeros and all ones show up as placeholders in descriptions.
private fun isUsableMacAddress(address: ByteArray): Boolean {
    return address.any { byte -> byte != 0.toByte() } && address.any { byte -> byte != 0xFF.toByte() }
}

/** The packet that wakes the machine with [address]: six 0xFF bytes, then its address 16 times. */
fun magicPacket(address: ByteArray): ByteArray {
    val packet = ByteArray(MAC_BYTES * (1 + MAGIC_PACKET_REPEATS))
    packet.fill(0xFF.toByte(), 0, MAC_BYTES)
    for (repeat in 1..MAGIC_PACKET_REPEATS) {
        address.copyInto(packet, destinationOffset = repeat * MAC_BYTES)
    }
    return packet
}

/** The address that reaches every device of the network [hostAddress] is on, e.g. 192.168.1.255 for a /24. */
fun directedBroadcastAddress(hostAddress: ByteArray, prefixLength: Int): ByteArray {
    val broadcast = hostAddress.copyOf()
    for (index in broadcast.indices) {
        val bitsOfByteInNetwork = (prefixLength - index * 8).coerceIn(0, 8)
        val hostMask = 0xFF shr bitsOfByteInNetwork
        broadcast[index] = (broadcast[index].toInt() or hostMask).toByte()
    }
    return broadcast
}
