package com.letsmoe.otter.ui.notifications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.letsmoe.otter.api.OtterApi
import com.letsmoe.otter.notifications.NotificationItem
import com.letsmoe.otter.notifications.NotificationsRepository
import com.letsmoe.otter.profiles.ProfileSession
import com.letsmoe.otter.ui.LoadState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

private const val NOTIFICATION_LIMIT = 100

/** Every notification of the profile; opening the page marks them read. */
class NotificationsViewModel : ViewModel() {

    private val mutableState = MutableStateFlow<LoadState<List<NotificationItem>>>(LoadState.Loading)
    val state: StateFlow<LoadState<List<NotificationItem>>> = mutableState

    init {
        load()
    }

    private fun load() {
        val profileId = ProfileSession.activeProfile.value?.id ?: return
        viewModelScope.launch {
            OtterApi.awaitBackend()
            val notifications = NotificationsRepository.fetch(profileId, NOTIFICATION_LIMIT)
            mutableState.value = LoadState.Loaded(notifications)
            if (notifications.any { notification -> !notification.read }) {
                NotificationsRepository.markAllRead(profileId)
            }
        }
    }
}
