package com.letsmoe.otter.ui.browse

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.letsmoe.otter.api.OtterApi
import com.letsmoe.otter.graphql.type.MediaType
import com.letsmoe.otter.ui.media.MediaRepository
import com.letsmoe.otter.ui.media.RailItem
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

private const val RAIL_SIZE = 25

data class LibraryState(val movies: List<RailItem> = emptyList(), val series: List<RailItem> = emptyList(), val loaded: Boolean = false)

class LibraryViewModel : ViewModel() {

    private val mutableState = MutableStateFlow(LibraryState())
    val state: StateFlow<LibraryState> = mutableState

    init {
        viewModelScope.launch {
            OtterApi.awaitBackend()
            val movies = async { MediaRepository.browse(MediaType.MOVIE, null, RAIL_SIZE, 0) }
            val series = async { MediaRepository.browse(MediaType.SERIES, null, RAIL_SIZE, 0) }
            mutableState.value = LibraryState(movies.await(), series.await(), loaded = true)
        }
    }
}
