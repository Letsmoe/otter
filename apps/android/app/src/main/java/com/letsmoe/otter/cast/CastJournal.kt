package com.letsmoe.otter.cast

import com.letsmoe.otter.reporting.ErrorReporting
import java.util.UUID

private const val MAX_ENTRIES = 100
private val pollActions = setOf("GetTransportInfo", "GetPositionInfo")
// Counters that change every second; a poll answer is only new when something else changed.
private val ticking = Regex("<(RelTime|AbsTime|RelCount|AbsCount)>[^<]*</\\1>")

/** One SOAP call to the TV: what was sent and what came back. */
data class SoapExchange(
    val action: String,
    val request: String,
    val status: Int?,
    val response: String,
    val error: String?,
    val durationMilliseconds: Long,
)

/**
 * Everything said with the TV during one cast. The last calls go with an error
 * report; with cast logging on, each call also goes to PostHog logs under the
 * cast's ID (polls only when their answer changed). The backend tags the TV's stream requests
 * with the same ID (?cast=).
 */
class CastJournal(private val renderer: DlnaRenderer) {

    var castId = newCastId()
        private set
    private val entries = ArrayDeque<String>()
    private val lastPollAnswers = mutableMapOf<String, String>()

    /** Starts a new cast: a new ID, nothing recorded. */
    @Synchronized
    fun startCast() {
        castId = newCastId()
        entries.clear()
        lastPollAnswers.clear()
    }

    @Synchronized
    fun record(exchange: SoapExchange) {
        if (isUnchangedPoll(exchange)) {
            return
        }
        add(describe(exchange))
        ErrorReporting.logCast("TV ${exchange.action}", attributesOf(exchange))
    }

    /** A note of the player's own, e.g. what it decided about the formats. */
    @Synchronized
    fun note(message: String, attributes: Map<String, Any?> = emptyMap()) {
        add(message)
        ErrorReporting.logCast(message, attributes + baseAttributes())
    }

    @Synchronized
    fun text(): String {
        return entries.joinToString("\n\n")
    }

    private fun add(entry: String) {
        entries.addLast(entry)
        if (entries.size > MAX_ENTRIES) {
            entries.removeFirst()
        }
    }

    private fun isUnchangedPoll(exchange: SoapExchange): Boolean {
        if (exchange.action !in pollActions) {
            return false
        }
        val answer = "${exchange.status} ${exchange.error} ${exchange.response.replace(ticking, "")}"
        val unchanged = lastPollAnswers[exchange.action] == answer
        lastPollAnswers[exchange.action] = answer
        return unchanged
    }

    private fun describe(exchange: SoapExchange): String {
        val outcome = exchange.error ?: "HTTP ${exchange.status}"
        return "${exchange.action} → $outcome (${exchange.durationMilliseconds} ms)\n" +
            "sent: ${exchange.request}\nreceived: ${exchange.response}"
    }

    private fun attributesOf(exchange: SoapExchange): Map<String, Any?> {
        return baseAttributes() + mapOf(
            "soap_action" to exchange.action,
            "soap_request" to exchange.request,
            "soap_status" to exchange.status,
            "soap_response" to exchange.response,
            "soap_error" to exchange.error,
            "duration_ms" to exchange.durationMilliseconds,
        )
    }

    private fun baseAttributes(): Map<String, Any?> {
        return mapOf("cast_id" to castId, "tv_name" to renderer.name, "tv_model" to renderer.model)
    }
}

private fun newCastId(): String {
    return UUID.randomUUID().toString().replace("-", "").take(16)
}
