package com.letsmoe.otter.ui.player

import android.app.Application
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.letsmoe.otter.AppScope
import com.letsmoe.otter.cast.CastKeepAliveService
import com.letsmoe.otter.cast.TelevisionConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** A TV that stopped answering, and the title it played, from where it was. */
data class LostTelevision(val name: String, val request: PlayerRequest)

/**
 * What plays on the connected TV, independent of any screen: one [PlayerViewModel] that lives
 * until the title is stopped or the TV disconnected, while the user browses on.
 */
object TelevisionPlayback {

    private lateinit var application: Application
    private var store = ViewModelStore()

    private val mutablePlayer = MutableStateFlow<PlayerViewModel?>(null)
    /** The title playing on the TV; null when nothing does. */
    val player: StateFlow<PlayerViewModel?> = mutablePlayer

    private val mutableRemoteOpen = MutableStateFlow(false)
    /** Whether the remote overlay is shown. */
    val remoteOpen: StateFlow<Boolean> = mutableRemoteOpen

    private val mutableLostTelevision = MutableStateFlow<LostTelevision?>(null)
    /** The TV that was disconnected because it stopped answering; null once the user decided. */
    val lostTelevision: StateFlow<LostTelevision?> = mutableLostTelevision

    fun initialize(application: Application) {
        this.application = application
        // A Chromecast session can also end from the TV or the system's media controls.
        AppScope.launch {
            TelevisionConnection.target.collect { target ->
                if (target == null) {
                    stop()
                }
            }
        }
    }

    /** Starts [request] on the connected TV and opens the remote. */
    fun play(request: PlayerRequest) {
        val target = TelevisionConnection.target.value ?: return
        CastKeepAliveService.start(application, target.name)
        mutableLostTelevision.value = null
        mutableRemoteOpen.value = true
        val current = mutablePlayer.value
        if (current != null) {
            current.openRequest(request)
            return
        }
        val factory = viewModelFactory { initializer { PlayerViewModel(application, request, forTelevision = true) } }
        mutablePlayer.value = ViewModelProvider.create(store, factory)[PlayerViewModel::class]
    }

    /** Stops the title on the TV; the TV stays connected. */
    fun stop() {
        if (mutablePlayer.value == null) {
            return
        }
        mutablePlayer.value = null
        store.clear()
        store = ViewModelStore()
        // A TV with its own session stream still needs the phone awake; disconnecting lets it sleep.
        if (!TelevisionConnection.streamsContinuously) {
            CastKeepAliveService.stop(application)
        }
    }

    /** Stops the title and the connection. */
    fun disconnect() {
        stop()
        mutableRemoteOpen.value = false
        TelevisionConnection.disconnect()
    }

    /** The title continues on the phone where the TV was: the returned request opens the player screen. */
    fun continueOnPhone(): PlayerRequest? {
        val current = mutablePlayer.value ?: return null
        val request = current.currentRequestAt()
        disconnect()
        return request
    }

    /** The TV stopped answering while playing [request]: it is disconnected and the user asked how to go on. */
    fun onTelevisionLost(request: PlayerRequest) {
        val target = TelevisionConnection.target.value ?: return
        mutableLostTelevision.value = LostTelevision(target.name, request)
        // Posted: the failing TV player is still notifying its listeners and is released by disconnect().
        AppScope.launch(Dispatchers.Main) { disconnect() }
    }

    /** The title of the lost TV, to open on the phone's player screen. */
    fun continueLostOnPhone(): PlayerRequest? {
        val lost = mutableLostTelevision.value ?: return null
        mutableLostTelevision.value = null
        return lost.request
    }

    fun dismissLostTelevision() {
        mutableLostTelevision.value = null
    }

    fun openRemote() {
        mutableRemoteOpen.value = true
    }

    fun closeRemote() {
        mutableRemoteOpen.value = false
    }
}
