package com.letsmoe.otter.ui.watchlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.letsmoe.otter.api.OtterApi
import com.letsmoe.otter.profiles.ProfileSession
import com.letsmoe.otter.ui.media.MediaRepository
import com.letsmoe.otter.ui.media.RailItem
import com.letsmoe.otter.ui.media.TitleActions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class WatchlistState(val items: List<RailItem> = emptyList(), val loaded: Boolean = false)

class WatchlistViewModel : ViewModel() {

    private val mutableState = MutableStateFlow(WatchlistState())
    val state: StateFlow<WatchlistState> = mutableState

    private val profileId: String = ProfileSession.activeProfile.value?.id.orEmpty()

    /** Reloaded each time the tab is shown. */
    fun load() {
        viewModelScope.launch {
            OtterApi.awaitBackend()
            mutableState.value = WatchlistState(MediaRepository.watchlist(profileId), loaded = true)
        }
    }

    fun remove(item: RailItem) {
        mutableState.update { current -> current.copy(items = current.items.filter { candidate -> candidate.id != item.id }) }
        viewModelScope.launch {
            try {
                TitleActions.setInWatchlist(profileId, item.id, false)
            } catch (error: Exception) {
                load()
            }
        }
    }
}
