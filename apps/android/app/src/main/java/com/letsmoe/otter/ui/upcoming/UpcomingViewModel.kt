package com.letsmoe.otter.ui.upcoming

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.letsmoe.otter.api.OtterApi
import com.letsmoe.otter.api.dataOrThrow
import com.letsmoe.otter.graphql.UpcomingEpisodesQuery
import com.letsmoe.otter.profiles.ProfileSession
import com.letsmoe.otter.ui.LoadState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate

/** The coming episodes of the profile's series, grouped by day. */
class UpcomingViewModel : ViewModel() {

    private val mutableState = MutableStateFlow<LoadState<List<UpcomingDay>>>(LoadState.Loading)
    val state: StateFlow<LoadState<List<UpcomingDay>>> = mutableState

    private val profileId: String = ProfileSession.activeProfile.value?.id.orEmpty()

    init {
        load()
    }

    fun load() {
        mutableState.value = LoadState.Loading
        viewModelScope.launch {
            OtterApi.awaitBackend()
            mutableState.value = fetchDays()
        }
    }

    private suspend fun fetchDays(): LoadState<List<UpcomingDay>> {
        try {
            val query = UpcomingEpisodesQuery(profileId, UPCOMING_DAYS)
            val data = OtterApi.client.query(query).execute().dataOrThrow()
            val episodes = data.upcomingEpisodes.mapNotNull { entry -> entry.toUpcomingEpisode() }
            return LoadState.Loaded(groupByDay(episodes, LocalDate.now()))
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            return LoadState.Failed(error.message.orEmpty())
        }
    }
}
