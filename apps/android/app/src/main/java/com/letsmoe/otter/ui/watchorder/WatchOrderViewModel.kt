package com.letsmoe.otter.ui.watchorder

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.letsmoe.otter.api.OtterApi
import com.letsmoe.otter.profiles.ProfileSession
import com.letsmoe.otter.ui.LoadState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

/** Loads one franchise's guide for the active profile. */
class WatchOrderViewModel(private val collectionId: String) : ViewModel() {

    private val mutableState = MutableStateFlow<LoadState<WatchOrderGuide>>(LoadState.Loading)
    val state: StateFlow<LoadState<WatchOrderGuide>> = mutableState

    private val profileId: String = ProfileSession.activeProfile.value?.id.orEmpty()

    init {
        load()
    }

    fun load() {
        mutableState.value = LoadState.Loading
        viewModelScope.launch { fetch() }
    }

    /** Picks up progress made since the page was last shown, without a loading state. */
    fun refresh() {
        if (mutableState.value !is LoadState.Loaded) {
            return
        }
        viewModelScope.launch { fetch() }
    }

    private suspend fun fetch() {
        OtterApi.awaitBackend()
        try {
            mutableState.value = LoadState.Loaded(WatchOrderRepository.guide(collectionId, profileId))
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            keepLoadedOrFail(error)
        }
    }

    private fun keepLoadedOrFail(error: Exception) {
        if (mutableState.value is LoadState.Loaded) {
            return
        }
        mutableState.value = LoadState.Failed(error.message.orEmpty())
    }
}
