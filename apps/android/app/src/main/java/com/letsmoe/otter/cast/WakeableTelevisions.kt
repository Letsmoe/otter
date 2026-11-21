package com.letsmoe.otter.cast

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

/**
 * A TV that can be switched on from standby: its MAC address and what Otter needs to connect to
 * it once it is awake ([renderer] is how the TV looked when it was last on).
 */
data class WakeableTelevision(val macAddress: String, val renderer: DlnaRenderer)

private const val FIELD_SEPARATOR = '\t'
private const val FIELD_COUNT = 9

fun encodeWakeableTelevision(television: WakeableTelevision): String {
    val renderer = television.renderer
    val fields = listOf(
        television.macAddress,
        renderer.id,
        renderer.controlUrl,
        renderer.serviceType,
        renderer.volumeControl?.controlUrl.orEmpty(),
        renderer.volumeControl?.serviceType.orEmpty(),
        renderer.connectionManager?.controlUrl.orEmpty(),
        renderer.connectionManager?.serviceType.orEmpty(),
        renderer.name,
    )
    return fields.joinToString(FIELD_SEPARATOR.toString())
}

fun decodeWakeableTelevision(entry: String): WakeableTelevision? {
    val fields = entry.split(FIELD_SEPARATOR, limit = FIELD_COUNT)
    if (fields.size != FIELD_COUNT) {
        return null
    }
    val renderer = DlnaRenderer(
        name = fields[8],
        id = fields[1],
        controlUrl = fields[2],
        serviceType = fields[3],
        volumeControl = serviceOrNull(fields[4], fields[5]),
        connectionManager = serviceOrNull(fields[6], fields[7]),
    )
    return WakeableTelevision(fields[0], renderer)
}

private fun serviceOrNull(controlUrl: String, serviceType: String): DlnaService? {
    if (controlUrl.isEmpty()) {
        return null
    }
    return DlnaService(controlUrl, serviceType)
}

/** The MAC addresses of the TVs, saved on this device by the TV's UDN. */
object WakeableTelevisions {

    private const val PREFERENCES = "wakeable_televisions"

    private lateinit var preferences: SharedPreferences

    fun initialize(context: Context) {
        preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
    }

    fun all(): List<WakeableTelevision> {
        return preferences.all.values.mapNotNull { entry -> decodeWakeableTelevision(entry.toString()) }
    }

    fun macAddressOf(televisionId: String): String? {
        val entry = preferences.getString(televisionId, null) ?: return null
        return decodeWakeableTelevision(entry)?.macAddress
    }

    /** Saves the address the viewer typed for [renderer]. */
    fun save(renderer: DlnaRenderer, macAddress: String) {
        val television = WakeableTelevision(macAddress, renderer)
        preferences.edit { putString(renderer.id, encodeWakeableTelevision(television)) }
    }

    fun forget(televisionId: String) {
        preferences.edit { remove(televisionId) }
    }

    /**
     * Notes a TV that is on: its address is read from its description when it gives one and the
     * viewer didn't type one. A TV with a saved address keeps its latest description.
     */
    fun learn(renderer: DlnaRenderer) {
        val macAddress = macAddressOf(renderer.id) ?: findMacAddress(renderer.description) ?: return
        save(renderer, macAddress)
    }

    /** The TVs with an address that are not among the [answering] ones, i.e. asleep or off. */
    fun inStandby(answering: List<DlnaRenderer>): List<WakeableTelevision> {
        val answeringIds = answering.map { renderer -> renderer.id }.toSet()
        return all().filter { television -> television.renderer.id !in answeringIds }
    }
}
