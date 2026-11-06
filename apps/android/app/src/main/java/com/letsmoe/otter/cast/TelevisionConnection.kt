package com.letsmoe.otter.cast

import android.content.Context
import android.util.Log
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.SessionManagerListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.roundToInt

private const val TAG = "OtterTelevision"

/** The TV the app is connected to. */
sealed interface TelevisionTarget {
    val name: String

    /** A DLNA TV; [player] drives it for as long as the connection lasts. */
    data class Dlna(val renderer: DlnaRenderer, val player: DlnaPlayer) : TelevisionTarget {
        override val name: String
            get() = renderer.name
    }

    /** A Chromecast; the Cast SDK's session is the connection. */
    data class Chromecast(override val name: String) : TelevisionTarget
}

/**
 * The connection to a TV, independent of any screen: connect once from the home screen, then
 * every title started plays there until the user disconnects.
 */
object TelevisionConnection {

    private val mutableTarget = MutableStateFlow<TelevisionTarget?>(null)
    val target: StateFlow<TelevisionTarget?> = mutableTarget

    private lateinit var applicationContext: Context

    // Called back for Chromecast sessions, including ones started from another app or the system.
    private val castSessionListener = object : SessionManagerListener<CastSession> {
        override fun onSessionStarted(session: CastSession, sessionId: String) = onChromecastConnected(session)
        override fun onSessionResumed(session: CastSession, wasSuspended: Boolean) = onChromecastConnected(session)
        override fun onSessionEnded(session: CastSession, error: Int) = onChromecastDisconnected()
        override fun onSessionStarting(session: CastSession) = Unit
        override fun onSessionStartFailed(session: CastSession, error: Int) = Unit
        override fun onSessionEnding(session: CastSession) = Unit
        override fun onSessionResuming(session: CastSession, sessionId: String) = Unit
        override fun onSessionResumeFailed(session: CastSession, error: Int) = Unit
        override fun onSessionSuspended(session: CastSession, reason: Int) = Unit
    }

    fun initialize(context: Context) {
        applicationContext = context.applicationContext
        val castContext = castContext() ?: return
        castContext.sessionManager.addSessionManagerListener(castSessionListener, CastSession::class.java)
        castContext.sessionManager.currentCastSession?.let(::onChromecastConnected)
    }

    val isConnected: Boolean
        get() = mutableTarget.value != null

    fun connectDlna(renderer: DlnaRenderer) {
        disconnect()
        mutableTarget.value = TelevisionTarget.Dlna(renderer, DlnaPlayer(applicationContext, renderer))
    }

    /** Opens the system's Chromecast picker; the session listener connects once one is picked. */
    fun connectChromecast(context: Context) {
        showChromecastPicker(context)
    }

    /** Stops the TV and forgets it. */
    fun disconnect() {
        val current = mutableTarget.value ?: return
        mutableTarget.value = null
        when (current) {
            is TelevisionTarget.Dlna -> current.player.release()
            is TelevisionTarget.Chromecast -> CastSetup.endSession(applicationContext)
        }
    }

    // Volume ---------------------------------------------------------------------

    val supportsVolume: Boolean
        get() {
            val current = mutableTarget.value ?: return false
            if (current is TelevisionTarget.Dlna) {
                return current.player.supportsVolume
            }
            return true
        }

    /** The TV's volume from 0 to 1; null when unknown. */
    suspend fun volume(): Float? {
        val current = mutableTarget.value ?: return null
        if (current is TelevisionTarget.Dlna) {
            val volume = current.player.readVolume() ?: return null
            return volume / 100f
        }
        return castSession()?.volume?.toFloat()
    }

    fun setVolume(fraction: Float) {
        val current = mutableTarget.value ?: return
        val clamped = fraction.coerceIn(0f, 1f)
        if (current is TelevisionTarget.Dlna) {
            current.player.changeVolume((clamped * 100).roundToInt())
            return
        }
        try {
            castSession()?.volume = clamped.toDouble()
        } catch (error: Exception) {
            Log.w(TAG, "setting the Chromecast's volume failed", error)
        }
    }

    // Chromecast ------------------------------------------------------------------

    private fun onChromecastConnected(session: CastSession) {
        val current = mutableTarget.value
        if (current is TelevisionTarget.Dlna) {
            current.player.release()
        }
        val name = session.castDevice?.friendlyName ?: "Chromecast"
        mutableTarget.value = TelevisionTarget.Chromecast(name)
    }

    private fun onChromecastDisconnected() {
        if (mutableTarget.value is TelevisionTarget.Chromecast) {
            mutableTarget.value = null
        }
    }

    private fun castContext(): CastContext? {
        return try {
            CastContext.getSharedInstance(applicationContext)
        } catch (error: Exception) {
            // No Google Play services: DLNA only.
            null
        }
    }

    private fun castSession(): CastSession? {
        return castContext()?.sessionManager?.currentCastSession
    }
}
