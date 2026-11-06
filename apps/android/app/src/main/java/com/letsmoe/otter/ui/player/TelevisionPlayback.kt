package com.letsmoe.otter.ui.player

import android.app.Application
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.letsmoe.otter.AppScope
import com.letsmoe.otter.cast.CastKeepAliveService
import com.letsmoe.otter.cast.TelevisionConnection
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

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
        CastKeepAliveService.stop(application)
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

    fun openRemote() {
        mutableRemoteOpen.value = true
    }

    fun closeRemote() {
        mutableRemoteOpen.value = false
    }
}
