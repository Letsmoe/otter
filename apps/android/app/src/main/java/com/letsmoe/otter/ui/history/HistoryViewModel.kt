package com.letsmoe.otter.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.letsmoe.otter.api.OtterApi
import com.letsmoe.otter.profiles.ProfileSession
import com.letsmoe.otter.ui.media.MediaRepository
import com.letsmoe.otter.ui.media.RailItem
import com.letsmoe.otter.ui.media.dayLabel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

private const val HISTORY_SIZE = 100

data class HistoryDay(val label: String, val items: List<RailItem>)

data class HistoryState(val days: List<HistoryDay> = emptyList(), val loaded: Boolean = false)

class HistoryViewModel : ViewModel() {

    private val mutableState = MutableStateFlow(HistoryState())
    val state: StateFlow<HistoryState> = mutableState

    init {
        viewModelScope.launch {
            OtterApi.awaitBackend()
            val profileId = ProfileSession.activeProfile.value?.id.orEmpty()
            val items = MediaRepository.history(profileId, HISTORY_SIZE)
            mutableState.value = HistoryState(groupByDay(items), loaded = true)
        }
    }

    /** Consecutive entries of one day share a section. */
    private fun groupByDay(items: List<RailItem>): List<HistoryDay> {
        val days = mutableListOf<HistoryDay>()
        for (item in items) {
            val label = labelOf(item)
            val last = days.lastOrNull()
            if (last != null && last.label == label) {
                days[days.lastIndex] = last.copy(items = last.items + item)
                continue
            }
            days.add(HistoryDay(label, listOf(item)))
        }
        return days
    }

    private fun labelOf(item: RailItem): String {
        if (item.watchedAt == null) {
            return "Earlier"
        }
        return dayLabel(item.watchedAt)
    }
}
