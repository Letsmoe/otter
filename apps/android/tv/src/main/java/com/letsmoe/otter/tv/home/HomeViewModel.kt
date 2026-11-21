package com.letsmoe.otter.tv.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.letsmoe.otter.api.OtterApi
import com.letsmoe.otter.api.dataOrThrow
import com.letsmoe.otter.graphql.TrendingQuery
import com.letsmoe.otter.graphql.fragment.TitleCard
import kotlinx.coroutines.launch

sealed interface HomeState {
    data object Loading : HomeState
    data class Failed(val message: String) : HomeState
    data class Loaded(val trending: List<TitleCard>) : HomeState
}

class HomeViewModel : ViewModel() {

    var state: HomeState by mutableStateOf(HomeState.Loading)
        private set

    init {
        load()
    }

    fun load() {
        state = HomeState.Loading
        viewModelScope.launch {
            state = fetchTrending()
        }
    }

    private suspend fun fetchTrending(): HomeState {
        return try {
            OtterApi.awaitBackend()
            val data = OtterApi.client.query(TrendingQuery()).execute().dataOrThrow()
            HomeState.Loaded(data.trending.map { title -> title.titleCard })
        } catch (error: Exception) {
            HomeState.Failed(error.message.orEmpty())
        }
    }
}
